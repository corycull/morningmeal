package app.morningmeal

import android.app.Application
import app.morningmeal.data.FoodDatabase
import app.morningmeal.data.JournalDatabase
import app.morningmeal.data.Repository
import app.morningmeal.data.SettingsStore

/** Manual dependency wiring: small app, one repository, no DI framework needed. */
class MorningmealApp : Application() {
    lateinit var repository: Repository
        private set

    override fun onCreate() {
        super.onCreate()
        repository = Repository(
            foods = FoodDatabase(this),
            db = JournalDatabase.create(this),
            settings = SettingsStore(this),
        )
    }
}
