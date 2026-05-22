package com.matteo.rosterenhancer.ui.components

import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun WheelPicker(
    items: List<String>,
    selectedItem: String,
    onItemSelected: (String) -> Unit,
    modifier: Modifier = Modifier,
    itemHeight: androidx.compose.ui.unit.Dp = 48.dp
) {
    val listState = rememberLazyListState()
    val flingBehavior = rememberSnapFlingBehavior(lazyListState = listState)
    
    // Auto-scroll to selectedItem on first composition
    val initialIndex = items.indexOf(selectedItem).takeIf { it >= 0 } ?: 0
    LaunchedEffect(initialIndex) {
        listState.scrollToItem(initialIndex)
    }

    // Update derived state
    val centralIndex by remember {
        derivedStateOf {
            val layoutInfo = listState.layoutInfo
            val visibleItems = layoutInfo.visibleItemsInfo
            if (visibleItems.isEmpty()) return@derivedStateOf -1
            
            val viewportCenter = layoutInfo.viewportStartOffset + layoutInfo.viewportSize.height / 2
            
            var matchedIndex = visibleItems.first().index
            var minDistance = Int.MAX_VALUE
            
            for (itemInfo in visibleItems) {
                val itemCenter = itemInfo.offset + itemInfo.size / 2
                val distance = kotlin.math.abs(itemCenter - viewportCenter)
                if (distance < minDistance) {
                    minDistance = distance
                    matchedIndex = itemInfo.index
                }
            }
            matchedIndex
        }
    }
    
    LaunchedEffect(centralIndex) {
        if (centralIndex in items.indices && items[centralIndex] != selectedItem) {
            onItemSelected(items[centralIndex])
        }
    }

    Box(modifier = modifier.height(itemHeight * 3), contentAlignment = Alignment.Center) {
        // Selection Highlight
        Surface(
            modifier = Modifier.fillMaxWidth().height(itemHeight),
            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
            shape = RoundedCornerShape(8.dp)
        ) {}
        
        LazyColumn(
            state = listState,
            flingBehavior = flingBehavior,
            contentPadding = PaddingValues(vertical = itemHeight),
            modifier = Modifier.fillMaxSize()
        ) {
            items(items.size) { index ->
                val isSelected = index == centralIndex
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(itemHeight),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = items[index],
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                        color = if (isSelected) MaterialTheme.colorScheme.primary else 
MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                    )
                }
            }
        }
    }
}
