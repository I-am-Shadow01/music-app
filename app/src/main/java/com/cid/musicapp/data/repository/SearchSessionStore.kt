package com.cid.musicapp.data.repository

import org.schabi.newpipe.extractor.Page
import org.schabi.newpipe.extractor.search.SearchExtractor
import java.util.concurrent.atomic.AtomicInteger

/** One active search. Generation check + publication share a lock; network never holds it. */
internal class SearchSessionStore {
    data class Session(val generation: Int, val extractor: SearchExtractor, val nextPage: Page?)

    private val generation = AtomicInteger(0)
    private val lock = Any()
    private var session: Session? = null

    fun invalidate(): Int = synchronized(lock) {
        session = null
        generation.incrementAndGet()
    }

    fun snapshot(): Session? = synchronized(lock) { session }

    fun publish(value: Session) = synchronized(lock) {
        if (value.generation == generation.get()) session = value
    }

    fun advance(expected: Session, nextPage: Page?) = synchronized(lock) {
        if (session === expected && expected.generation == generation.get()) {
            session = expected.copy(nextPage = nextPage)
        }
    }
}
