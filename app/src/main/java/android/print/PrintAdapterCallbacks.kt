package android.print

/**
 * Openable subclasses of the print framework's two result callbacks.
 *
 * `PrintDocumentAdapter.LayoutResultCallback` and `WriteResultCallback` are public abstract classes
 * with package-private constructors. Declaring them here in `android.print` allows driving
 * PrintDocumentAdapter directly to convert WebView content to PDF without UI dialogs.
 */
abstract class OpenLayoutResultCallback : PrintDocumentAdapter.LayoutResultCallback()

abstract class OpenWriteResultCallback : PrintDocumentAdapter.WriteResultCallback()
