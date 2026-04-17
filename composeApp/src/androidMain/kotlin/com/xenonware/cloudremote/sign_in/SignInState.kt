package com.xenonware.cloudremote.sign_in

data class SignInState(
    val isSignInSuccessful: Boolean = false,
    val signInError: String? = null,
    val userData: UserData? = null
)
