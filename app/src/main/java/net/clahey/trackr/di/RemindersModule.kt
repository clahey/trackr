package net.clahey.trackr.di

import android.app.AlarmManager
import android.app.NotificationManager
import android.content.Context
import net.clahey.trackr.data.AlarmScheduler
import net.clahey.trackr.data.ReminderNotifier
import net.clahey.trackr.data.local.AndroidAlarmScheduler
import net.clahey.trackr.data.local.AndroidReminderNotifier
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

// @spec APP-REM-001
@Module
@InstallIn(SingletonComponent::class)
abstract class RemindersModule {

    @Binds
    @Singleton
    abstract fun bindAlarmScheduler(impl: AndroidAlarmScheduler): AlarmScheduler

    @Binds
    @Singleton
    abstract fun bindReminderNotifier(impl: AndroidReminderNotifier): ReminderNotifier

    companion object {
        @Provides
        @Singleton
        fun provideAlarmManager(@ApplicationContext context: Context): AlarmManager =
            context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

        @Provides
        @Singleton
        fun provideNotificationManager(@ApplicationContext context: Context): NotificationManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    }
}
