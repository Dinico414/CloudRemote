package com.xenonware.cloudremote.ui.res

import android.annotation.SuppressLint
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.xenon.mylibrary.theme.QuicksandTitleVariable
import com.xenonware.cloudremote.R

@SuppressLint("LocalContextResourcesRead")
@Composable
fun LoginScreen(
    modifier: Modifier = Modifier,
    onSignInClick: () -> Unit,
) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Button(
            onClick = {
                onSignInClick()
            }, modifier = Modifier
                .fillMaxWidth()
                .height(96.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    painter = painterResource(id = R.drawable.googleicon),
                    tint = Color.Black.copy(alpha = 0.25f),
                    contentDescription = null,
                    modifier = Modifier
                        .offset(x = 1.dp, y = 2.dp)
                        .blur(radius = 2.dp)
                        .padding(5.dp)

                )
                Icon(
                    painter = painterResource(id = R.drawable.googleicon),
                    tint = Color.Unspecified,
                    contentDescription = stringResource(R.string.sign_in),
                )
            }
            Spacer(modifier = Modifier.width(16.dp))
            Text(
                text = stringResource(R.string.sign_in),
                style = MaterialTheme.typography.headlineMedium,
                fontFamily = QuicksandTitleVariable,
            )
        }
    }
}
