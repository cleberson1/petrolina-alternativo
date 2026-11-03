package org.petrolinaalternativo.app

import android.app.Application

/** Application customizada: agenda a atualização diária do JSON ao iniciar. */
class App : Application() {
    override fun onCreate() {
        super.onCreate()
        AtualizadorWorker.agendar(this)
    }
}
