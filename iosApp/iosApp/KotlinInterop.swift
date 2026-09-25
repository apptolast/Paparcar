/// Bridges one rough edge of the Kotlin/Native export.
///
/// A Kotlin `(String?) -> Unit` that sits **nested** inside another lambda — which is what every
/// `signInHandler` and `revokeHandler` in this library is — comes out as a block returning
/// `KotlinUnit`, not `void`: an Objective-C block cannot express `void` in that position. So the
/// `completion` these handlers hand to Swift is `(String?) -> KotlinUnit`, and passing it straight
/// into anything declared `(String?) -> Void` fails to compile.
///
/// This drops that result so the rest of the app keeps speaking plain Swift. It is generic on the
/// return type on purpose: nothing here has to name `KotlinUnit`, so it survives the exporter
/// mapping it differently.
func discardingResult<T>(_ body: @escaping (String?) -> T) -> (String?) -> Void {
    { token in
        _ = body(token)
    }
}
