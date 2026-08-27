//! Helper binary for `cargo run --bin uniffi-bindgen`
//! Used by Gradle task `uniffiBindgen` to generate Kotlin bindings.

fn main() {
    uniffi::uniffi_bindgen_main()
}
