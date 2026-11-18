package com.example.stickerwidget.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.view.View
import android.widget.RemoteViews
import com.example.stickerwidget.MainActivity
import com.example.stickerwidget.R
import com.example.stickerwidget.data.StickerRepository

class StickerWidgetProvider : AppWidgetProvider() {

    companion object {
        const val ACTION_PIN_SUCCESS = "com.example.stickerwidget.ACTION_PIN_WIDGET_SUCCESS"
        const val EXTRA_STICKER_ID = "extra_sticker_id"

        fun updateAppWidget(
            context: Context,
            appWidgetManager: AppWidgetManager,
            appWidgetId: Int,
            repository: StickerRepository = StickerRepository(context)
        ) {
            val views = RemoteViews(context.packageName, R.layout.widget_sticker)
            val sticker = repository.getStickerForWidget(appWidgetId)

            if (sticker != null) {
                val bitmap = repository.loadBitmap(sticker.filePath)
                if (bitmap != null) {
                    views.setImageViewBitmap(R.id.widget_sticker_image, bitmap)
                    views.setViewVisibility(R.id.widget_sticker_image, View.VISIBLE)
                    views.setViewVisibility(R.id.widget_empty_text, View.GONE)
                } else {
                    views.setViewVisibility(R.id.widget_sticker_image, View.GONE)
                    views.setViewVisibility(R.id.widget_empty_text, View.VISIBLE)
                }
            } else {
                // If no specific sticker is assigned yet, load the latest one if available
                val latest = repository.getAllStickers().firstOrNull()
                if (latest != null) {
                    repository.setWidgetStickerId(appWidgetId, latest.id)
                    val bitmap = repository.loadBitmap(latest.filePath)
                    if (bitmap != null) {
                        views.setImageViewBitmap(R.id.widget_sticker_image, bitmap)
                        views.setViewVisibility(R.id.widget_sticker_image, View.VISIBLE)
                        views.setViewVisibility(R.id.widget_empty_text, View.GONE)
                    }
                } else {
                    views.setViewVisibility(R.id.widget_sticker_image, View.GONE)
                    views.setViewVisibility(R.id.widget_empty_text, View.VISIBLE)
                }
            }

            // Clicking widget launches MainActivity
            val intent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra(EXTRA_STICKER_ID, sticker?.id)
            }
            val pendingIntent = PendingIntent.getActivity(
                context,
                appWidgetId,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widget_root, pendingIntent)

            appWidgetManager.updateAppWidget(appWidgetId, views)
        }

        fun updateAllWidgets(context: Context) {
            val appWidgetManager = AppWidgetManager.getInstance(context)
            val componentName = ComponentName(context, StickerWidgetProvider::class.java)
            val appWidgetIds = appWidgetManager.getAppWidgetIds(componentName)
            val repository = StickerRepository(context)
            for (widgetId in appWidgetIds) {
                updateAppWidget(context, appWidgetManager, widgetId, repository)
            }
        }
    }

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        val repository = StickerRepository(context)
        for (appWidgetId in appWidgetIds) {
            updateAppWidget(context, appWidgetManager, appWidgetId, repository)
        }
    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        val repository = StickerRepository(context)
        for (appWidgetId in appWidgetIds) {
            repository.removeWidgetAssociation(appWidgetId)
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == ACTION_PIN_SUCCESS) {
            val stickerId = intent.getStringExtra(EXTRA_STICKER_ID)
            val appWidgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
            if (stickerId != null && appWidgetId != AppWidgetManager.INVALID_APPWIDGET_ID) {
                val repository = StickerRepository(context)
                repository.setWidgetStickerId(appWidgetId, stickerId)
                val manager = AppWidgetManager.getInstance(context)
                updateAppWidget(context, manager, appWidgetId, repository)
            }
        }
    }
}
