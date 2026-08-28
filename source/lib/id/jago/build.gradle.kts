source {
    id = "jago"
    name = "Bank Jago"
}

// Being a source is configured from the root build: the Android library plugin,
// the namespace, the resource prefix, the icon, the processor and the test
// dependencies all come from where this module sits. Only what this one bank
// needs belongs below — and prefer teaching Receipt over adding a library,
// since every other source could use the same fix.
