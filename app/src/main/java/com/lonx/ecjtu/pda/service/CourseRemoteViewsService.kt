package com.lonx.ecjtu.pda.service

import android.content.Intent
import android.widget.RemoteViewsService

class CourseRemoteViewsService : RemoteViewsService() {

    override fun onGetViewFactory(intent: Intent): RemoteViewsFactory {
        return CourseRemoteViewsFactory(this.applicationContext, intent)
    }
}