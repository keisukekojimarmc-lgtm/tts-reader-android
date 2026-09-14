# Accessibility service and TTS classes are referenced from the manifest / framework;
# keep them so a future minified build does not strip them.
-keep class com.example.ttsreader.service.ReaderAccessibilityService { *; }
