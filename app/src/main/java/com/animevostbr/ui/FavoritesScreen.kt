package com.animevostbr.ui

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.animevostbr.core.Repo
import com.animevostbr.core.TitleLite

@Composable
fun FavoritesScreen(
    onOpen: (Int) -> Unit,
    onBack: () -> Unit
) {
    // observable list: removals show up immediately
    val list: SnapshotStateList<TitleLite> = remember { Repo.favorites().toMutableStateList() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(modifier = Modifier.focusBorder(CircleShape), onClick = onBack) { Icon(Icons.Filled.ArrowBack, contentDescription = "Назад") }
            Text(
                text = "Избранное",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(start = 8.dp)
            )
        }

        if (list.isEmpty()) {
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Здесь пока пусто.\nДобавляйте тайтлы долгим нажатием на карточку.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = 12.dp)
            ) {
                items(list, key = { it.id }) { lite ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        RowCard(
                            lite = lite,
                            subtitle = listOf(lite.year, lite.type).filter { it.isNotBlank() }
                                .joinToString(" • "),
                            onClick = { onOpen(lite.id) },
                            onLongClick = {
                                Repo.toggleFavorite(lite)
                                list.removeIf { it.id == lite.id }
                            },
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(modifier = Modifier.focusBorder(CircleShape), onClick = {
                            Repo.toggleFavorite(lite)
                            list.removeIf { it.id == lite.id }
                        }) {
                            Icon(Icons.Filled.Close, contentDescription = "Убрать из избранного")
                        }
                    }
                }
            }
        }
    }
}
