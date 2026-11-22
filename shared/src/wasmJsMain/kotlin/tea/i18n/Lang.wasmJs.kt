package tea.i18n

/**
 * Overrides `navigator.language` / `navigator.languages` (once) so they answer the chosen app
 * language; Compose resources read the locale from there.
 */
private fun jsSetLocale(lang: String): Unit = js(
    """{
    if (!window.__teaLocalePatched) {
        window.__teaLocalePatched = true;
        var proto = Navigator.prototype;
        var one = Object.getOwnPropertyDescriptor(proto, 'language');
        var many = Object.getOwnPropertyDescriptor(proto, 'languages');
        Object.defineProperty(proto, 'language', { configurable: true, get: function () { return window.__teaLocale || one.get.call(this); } });
        Object.defineProperty(proto, 'languages', { configurable: true, get: function () { return window.__teaLocale ? [window.__teaLocale] : many.get.call(this); } });
    }
    window.__teaLocale = lang;
}""",
)

actual fun setPlatformLanguage(lang: String) {
    runCatching { jsSetLocale(lang) }
}
