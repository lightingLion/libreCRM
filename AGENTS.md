# Project constraints

- Treat 开发文档.md as the product requirements and visual specification.
- Implement only the current milestone. Do not add OCR, automatic dialing, call
  recording, call-log access, cloud sync, analytics, or marketing features early.
- Minimum Android version is API 28. Use Kotlin, Jetpack Compose, Material 3,
  Room, DataStore, and MVVM.
- Keep customer data local. Do not request permissions unless a shipped feature
  needs them. In particular, use ACTION_DIAL if dialing is added later.
- Define the PRD color tokens centrally in the theme and use those tokens across
  the UI. Preserve the paper, pine, ink, and warm editorial direction.
- Use real LXGW WenKai Regular, Medium, and Bold font files where available;
  keep the font's full license with the bundled assets.
- Keep stage boundaries visible in UI and documentation. Never present a
  placeholder as a working import or customer-management feature.
- Do not invent the final application ID, author contact, wallet address, or
  project license. The current application ID is a development placeholder.
- Build and inspect the affected module after each meaningful implementation
  step. Record only commands and results that actually occurred in TASKS.md.
