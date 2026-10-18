package com.aicameraultra.network

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BackendUrlTest {

    @Test
    fun acceptsHttpsAndStripsTrailingSlash() {
        assertEquals("https://api.ejemplo.com", BackendUrl.normalize(" https://api.ejemplo.com/ "))
    }

    @Test
    fun rejectsPlainHttpForRemoteHosts() {
        assertNull(BackendUrl.normalize("http://api.ejemplo.com"))
    }

    @Test
    fun allowsEmulatorLocalhost() {
        assertEquals("http://10.0.2.2:3000", BackendUrl.normalize("http://10.0.2.2:3000"))
    }

    @Test
    fun rejectsEmptyAndInvalid() {
        assertNull(BackendUrl.normalize(""))
        assertNull(BackendUrl.normalize("no es una url"))
        assertNull(BackendUrl.normalize("ftp://ejemplo.com"))
    }
}
