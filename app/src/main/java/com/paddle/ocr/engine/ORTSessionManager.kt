// Copyright (c) 2026 PaddlePaddle Authors. All Rights Reserved.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//     http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.paddle.ocr.engine

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import ai.onnxruntime.providers.NNAPIFlags
import android.content.Context
import android.os.Build
import android.util.Log
import com.paddle.ocr.EngineConfig
import com.paddle.ocr.model.OCRError
import java.nio.FloatBuffer
import java.util.EnumSet

class ORTSessionManager(
    private val context: Context,
    private val config: EngineConfig,
) {
    private var env: OrtEnvironment? = null
    private var detSession: OrtSession? = null
    private var recSession: OrtSession? = null
    private var detInputName: String = "x"
    private var recInputName: String = "x"
    private var detAssetPath: String = ""
    private var recAssetPath: String = ""
    private var usingNnapi = false
    private var nnapiCpuDisabled = false
    val executionProvider: String
        get() = when {
            !usingNnapi -> "CPU"
            nnapiCpuDisabled -> "NNAPI (Qualcomm CPU disabled)"
            else -> "NNAPI"
        }
    var coldLoadTimeMs: Long = 0
        private set

    fun loadModels(detAssetPath: String, recAssetPath: String) {
        val loadStart = System.currentTimeMillis()
        this.detAssetPath = detAssetPath
        this.recAssetPath = recAssetPath
        try {
            createSessions(useNnapi = config.preferNnapi)
        } catch (providerError: Exception) {
            if (!config.preferNnapi) throw providerError
            Log.w(TAG, "NNAPI session creation failed; retrying with CPU", providerError)
            createSessions(useNnapi = false)
        }
        coldLoadTimeMs = System.currentTimeMillis() - loadStart
    }

    fun runDetection(input: FloatArray, shape: LongArray): Pair<FloatArray, LongArray> {
        val session = detSession
            ?: throw OCRError.ModelLoadFailed("detection", Exception("Session not initialized"))
        val ortEnv = env
            ?: throw OCRError.ModelLoadFailed("detection", Exception("Environment not initialized"))
        return runWithFallback(ortEnv, session, detInputName, input, shape, "detection")
    }

    fun runRecognition(input: FloatArray, shape: LongArray): Pair<FloatArray, LongArray> {
        val session = recSession
            ?: throw OCRError.ModelLoadFailed("recognition", Exception("Session not initialized"))
        val ortEnv = env
            ?: throw OCRError.ModelLoadFailed("recognition", Exception("Environment not initialized"))
        return runWithFallback(ortEnv, session, recInputName, input, shape, "recognition")
    }

    fun release() {
        try {
            detSession?.close()
        } finally {
            detSession = null
            try {
                recSession?.close()
            } finally {
                recSession = null
                env = null
            }
        }
    }

    private fun readModelAsset(assetPath: String): ByteArray {
        return try {
            context.assets.open(assetPath).use { it.readBytes() }
        } catch (t: Throwable) {
            throw OCRError.ModelNotFound(assetPath, t)
        }
    }

    @Synchronized
    private fun createSessions(useNnapi: Boolean) {
        closeSessions()
        val ortEnv = env ?: OrtEnvironment.getEnvironment().also { env = it }
        val disableNnapiCpu = useNnapi && shouldDisableNnapiCpu()
        val options = OrtSession.SessionOptions().apply {
            setOptimizationLevel(OrtSession.SessionOptions.OptLevel.ALL_OPT)
            setIntraOpNumThreads(config.numThreads)
            if (useNnapi) {
                val flags = if (disableNnapiCpu) {
                    EnumSet.of(NNAPIFlags.CPU_DISABLED)
                } else {
                    EnumSet.noneOf(NNAPIFlags::class.java)
                }
                addNnapi(flags)
            }
        }
        try {
            val detBytes = readModelAsset(detAssetPath)
            detSession = try {
                ortEnv.createSession(detBytes, options)
            } catch (t: Throwable) {
                throw OCRError.ModelLoadFailed("detection", t)
            }
            val recBytes = readModelAsset(recAssetPath)
            recSession = try {
                ortEnv.createSession(recBytes, options)
            } catch (t: Throwable) {
                throw OCRError.ModelLoadFailed("recognition", t)
            }
            detInputName = detSession!!.inputNames.iterator().next()
            recInputName = recSession!!.inputNames.iterator().next()
            usingNnapi = useNnapi
            nnapiCpuDisabled = disableNnapiCpu
            Log.i(TAG, "OCR execution provider configured: $executionProvider")
        } catch (t: Throwable) {
            closeSessions()
            throw t
        } finally {
            options.close()
        }
    }

    @Synchronized
    private fun runWithFallback(
        ortEnv: OrtEnvironment,
        session: OrtSession,
        inputName: String,
        input: FloatArray,
        shape: LongArray,
        modelName: String,
    ): Pair<FloatArray, LongArray> {
        return try {
            runSession(ortEnv, session, inputName, input, shape, modelName)
        } catch (acceleratorError: Exception) {
            if (!usingNnapi) throw acceleratorError
            Log.w(TAG, "NNAPI inference failed; switching to CPU", acceleratorError)
            createSessions(useNnapi = false)
            val cpuSession = (if (modelName == "detection") detSession else recSession)
                ?: throw OCRError.ModelLoadFailed(modelName, Exception("CPU session not initialized"))
            val cpuInputName = if (modelName == "detection") detInputName else recInputName
            runSession(env!!, cpuSession, cpuInputName, input, shape, modelName)
        }
    }

    private fun closeSessions() {
        try {
            detSession?.close()
        } finally {
            detSession = null
            try {
                recSession?.close()
            } finally {
                recSession = null
            }
        }
    }

    private fun runSession(
        ortEnv: OrtEnvironment,
        session: OrtSession,
        inputName: String,
        input: FloatArray,
        shape: LongArray,
        modelName: String,
    ): Pair<FloatArray, LongArray> {
        val tensor = try {
            OnnxTensor.createTensor(ortEnv, FloatBuffer.wrap(input), shape)
        } catch (t: Throwable) {
            throw OCRError.InferenceFailed(modelName, t)
        }
        val result = try {
            try {
                session.run(mapOf(inputName to tensor))
            } catch (t: Throwable) {
                throw OCRError.InferenceFailed(modelName, t)
            }
        } finally {
            tensor.close()
        }

        return try {
            try {
                val outputName = session.outputNames.iterator().next()
                val ortValue = result.get(outputName)
                    .orElseThrow { Exception("No output tensor found") }
                val outputTensor = ortValue as? OnnxTensor
                    ?: throw Exception("Output is not an ONNX tensor")
                Pair(copyFloatBuffer(outputTensor.floatBuffer), outputTensor.info.shape)
            } catch (t: Throwable) {
                throw OCRError.InferenceFailed(modelName, t)
            }
        } finally {
            result.close()
        }
    }

    private fun copyFloatBuffer(buffer: FloatBuffer): FloatArray {
        val duplicate = buffer.duplicate()
        duplicate.rewind()
        val output = FloatArray(duplicate.remaining())
        duplicate.get(output)
        return output
    }

    private fun shouldDisableNnapiCpu(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return false
        val socManufacturer = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            Build.SOC_MANUFACTURER
        } else {
            ""
        }
        return socManufacturer.equals("QTI", ignoreCase = true) ||
            socManufacturer.equals("Qualcomm", ignoreCase = true) ||
            Build.HARDWARE.equals("qcom", ignoreCase = true)
    }

    companion object {
        private const val TAG = "PaddleOCR"
    }
}
