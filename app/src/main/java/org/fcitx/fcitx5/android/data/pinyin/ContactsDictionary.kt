/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 TapFeet Contributors
 */

package org.fcitx.fcitx5.android.data.pinyin

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.provider.ContactsContract
import org.fcitx.fcitx5.android.core.reloadPinyinCustomPhrase
import org.fcitx.fcitx5.android.daemon.FcitxConnection
import org.fcitx.fcitx5.android.data.pinyin.customphrase.PinyinCustomPhrase
import org.fcitx.fcitx5.android.data.prefs.AppPrefs
import timber.log.Timber
import java.io.File

/**
 * Turns address-book display names into pinyin custom phrases. Values we inserted are listed in
 * a sidecar file so a later sync can drop them without touching the user's own phrases.
 */
object ContactsDictionary {

    private const val SIDECAR = "contacts_dict_values.txt"
    private const val STAMP = "contacts_dict_sync_at"
    private const val THROTTLE_MS = 30 * 60 * 1000L

    fun maybeSync(context: Context, fcitx: FcitxConnection, force: Boolean = false) {
        val on = AppPrefs.getInstance().candidateBar.contactsDictionary.getValue()
        if (!on) {
            if (sidecar(context).isFile) clear(context, fcitx)
            return
        }
        if (context.checkSelfPermission(Manifest.permission.READ_CONTACTS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        val stamp = stampFile(context)
        if (!force && stamp.isFile) {
            val last = stamp.readText().toLongOrNull() ?: 0L
            if (System.currentTimeMillis() - last < THROTTLE_MS) return
        }
        sync(context, fcitx)
        stamp.writeText(System.currentTimeMillis().toString())
    }

    fun clear(context: Context, fcitx: FcitxConnection) {
        val previous = loadSidecar(context)
        if (previous.isEmpty()) {
            sidecar(context).delete()
            stampFile(context).delete()
            return
        }
        try {
            val existing = (CustomPhraseManager.load() ?: emptyArray()).toMutableList()
            existing.removeAll { it.value in previous }
            CustomPhraseManager.save(existing.toTypedArray())
        } catch (e: Exception) {
            Timber.e(e, "ContactsDictionary.clear")
            return
        }
        sidecar(context).delete()
        stampFile(context).delete()
        fcitx.runIfReady { reloadPinyinCustomPhrase() }
        Timber.i("ContactsDictionary: cleared ${previous.size} names")
    }

    fun sync(context: Context, fcitx: FcitxConnection) {
        val names = readNames(context)
        val existing = try {
            (CustomPhraseManager.load() ?: emptyArray()).toMutableList()
        } catch (e: Exception) {
            Timber.e(e, "ContactsDictionary.sync load")
            return
        }
        val previous = loadSidecar(context)
        existing.removeAll { it.value in previous }
        val added = mutableListOf<String>()
        val seen = existing.map { it.key to it.value }.toMutableSet()
        for (name in names) {
            val key = PinyinLookup.pinyinOf(name) ?: continue
            if (key.isEmpty() || !key.all { it.isLetter() }) continue
            val phrase = PinyinCustomPhrase(key.lowercase(), 1, name)
            if (!seen.add(phrase.key to phrase.value)) continue
            existing += phrase
            added += name
        }
        try {
            CustomPhraseManager.save(existing.toTypedArray())
        } catch (e: Exception) {
            Timber.e(e, "ContactsDictionary.sync save")
            return
        }
        saveSidecar(context, added)
        fcitx.runIfReady { reloadPinyinCustomPhrase() }
        Timber.i("ContactsDictionary: synced ${added.size} names")
    }

    private fun readNames(context: Context): List<String> {
        val out = LinkedHashSet<String>()
        val uri = ContactsContract.Contacts.CONTENT_URI
        val projection = arrayOf(ContactsContract.Contacts.DISPLAY_NAME_PRIMARY)
        try {
            context.contentResolver.query(uri, projection, null, null, null)?.use { c ->
                val idx = c.getColumnIndex(ContactsContract.Contacts.DISPLAY_NAME_PRIMARY)
                if (idx < 0) return emptyList()
                while (c.moveToNext()) {
                    val name = c.getString(idx)?.trim().orEmpty()
                    if (name.any { Character.UnicodeBlock.of(it) == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS }) {
                        out += name
                    }
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "ContactsDictionary.readNames")
        }
        return out.toList()
    }

    private fun sidecar(context: Context) = File(context.filesDir, SIDECAR)

    private fun stampFile(context: Context) = File(context.filesDir, STAMP)

    private fun loadSidecar(context: Context): Set<String> {
        val f = sidecar(context)
        if (!f.isFile) return emptySet()
        return f.readLines().filter { it.isNotBlank() }.toSet()
    }

    private fun saveSidecar(context: Context, values: List<String>) {
        sidecar(context).writeText(values.joinToString("\n"))
    }
}
