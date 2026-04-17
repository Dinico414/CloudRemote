package com.xenonware.cloudremote

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform