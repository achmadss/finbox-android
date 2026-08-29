plugins {
    id("finbox.source")
}

source {
    id = "jago"
    name = "Bank Jago"
}

// Being a source is the plugin's job: the Android library setup, the namespace,
// the resource prefix, the icon, the processor and the test dependencies. Only
// what this one bank needs belongs below — and prefer teaching Receipt over
// adding a library, since every other source could use the same fix.
