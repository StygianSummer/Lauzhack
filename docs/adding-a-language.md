# Adding a language (no code change)

1. Copy `shared/src/commonMain/composeResources/values/strings.xml` to `values-<lang>/strings.xml` and translate the strings.
2. Copy each `content/**/<id>.en.json` (items, `lessons/*/lesson.en.json`, `field/*.en.json`) to `<id>.<lang>.json`; translate text fields only and set `"language": "<lang>"`.
3. Add the code to `content/languages.txt` (one per line); missing files fall back to English.
4. Run `./gradlew :shared:jvmTest` (validation warns about missing translations; `-Pstrict=true` makes them errors).
