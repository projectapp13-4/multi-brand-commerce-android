package com.gurbakir.mobile

import android.content.Context
import com.gurbakir.firebase.ControlledPushTarget
import java.io.File

class BuildVariantFirebaseProofTargetRecorder(context: Context) : FirebaseProofTargetRecorder {
    private val targetFile = File(context.cacheDir, FirebaseProofTargetRecorder.CACHE_FILE_NAME)

    override fun record(target: ControlledPushTarget) {
        target.use { value -> targetFile.writeText(value, Charsets.UTF_8) }
    }

    override fun clear() {
        if (targetFile.isFile) targetFile.delete()
    }
}
