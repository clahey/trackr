package net.clahey.trackr.reminders

import net.clahey.trackr.data.TrackrRepository

// Test fixture: a ReminderScheduler wired to no-op fakes, for ViewModel tests that don't
// exercise reminders themselves but need a real instance to satisfy the constructor.
fun testReminderScheduler(repository: TrackrRepository): ReminderScheduler =
    ReminderScheduler(repository, FakeAlarmScheduler(), FakeReminderNotifier(), FakePreferencesDataStore())
