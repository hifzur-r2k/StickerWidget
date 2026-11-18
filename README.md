# StickerWidget App

An Android app written in Kotlin & Jetpack Compose that turns photos into custom home screen sticker widgets with on-device AI subject cutout.

## Flow
1. **Photo Capture**: Capture a photo using the camera or select one from the gallery.
2. **AI Cutout**: On-device subject segmentation using Google ML Kit to remove the background.
3. **Sticker Styling**: Customize the die-cut border thickness, border color, drop shadow, text captions, and emoji badges.
4. **Widget Pinning**: 1-tap "Make Widget" action using Android's `AppWidgetManager.requestPinAppWidget` to pin the sticker directly onto the home screen.

## Project Structure
- `MainActivity.kt`: Handles camera capture, gallery selection, and navigation.
- `segmentation/StickerProcessor.kt`: ML Kit segmentation and die-cut border/shadow rendering.
- `widget/StickerWidgetProvider.kt`: Android `AppWidgetProvider` for rendering the home screen widget.
- `widget/WidgetPinHelper.kt`: Triggers the system launcher prompt to add the widget.
- `data/StickerRepository.kt`: Persistent storage for generated sticker images and widget associations.
- `ui/screens/HomeScreen.kt`: Gallery of created stickers with quick-pin actions.
- `ui/screens/StickerEditorScreen.kt`: Real-time sticker editing canvas with wallpaper preview.
