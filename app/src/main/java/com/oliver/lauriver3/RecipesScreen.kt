package com.oliver.lauriver3

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.storage.storage
import kotlinx.coroutines.launch
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.util.UUID

// ─────────────────────────────────────────────────────────────────────────────
// Data Models
// ─────────────────────────────────────────────────────────────────────────────

@Serializable
data class Recipe(
    val id: String = "",
    val title: String,
    val description: String? = null,
    @SerialName("source_type") val sourceType: String = "manual",
    @SerialName("image_url") val imageUrl: String? = null,
    val servings: Int = 2,
    @SerialName("prep_time_min") val prepTimeMin: Int? = null,
    @SerialName("cook_time_min") val cookTimeMin: Int? = null,
    val tags: List<String>? = null,
    @SerialName("created_at") val createdAt: String? = null
)

@Serializable
data class RecipeIngredient(
    val id: String = "",
    @SerialName("recipe_id") val recipeId: String = "",
    val name: String,
    val amount: String? = null,
    val unit: String? = null,
    @SerialName("sort_order") val sortOrder: Int = 0
)

@Serializable
data class RecipeStep(
    val id: String = "",
    @SerialName("recipe_id") val recipeId: String = "",
    @SerialName("step_number") val stepNumber: Int,
    val description: String
)

data class FullRecipe(
    val recipe: Recipe,
    val ingredients: List<RecipeIngredient>,
    val steps: List<RecipeStep>
)

// Lokaler Zustand für das Bearbeiten
data class EditIngredient(
    val id: String = UUID.randomUUID().toString(),
    var amount: String = "",
    var unit: String = "",
    var name: String = ""
)

data class EditStep(
    val id: String = UUID.randomUUID().toString(),
    var description: String = ""
)

// ─────────────────────────────────────────────────────────────────────────────
// Hauptscreen – Übersicht
// ─────────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecipesScreen() {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    var recipes by remember { mutableStateOf<List<Recipe>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }

    // Navigation
    var viewRecipe by remember { mutableStateOf<FullRecipe?>(null) }
    var showAddSheet by remember { mutableStateOf(false) }
    var editRecipe by remember { mutableStateOf<FullRecipe?>(null) }

    // Filter
    var searchQuery by remember { mutableStateOf("") }
    var selectedTag by remember { mutableStateOf<String?>(null) }

    fun load() {
        scope.launch {
            isLoading = true
            error = null
            try {
                recipes = supabase.from("recipes")
                    .select()
                    .decodeList<Recipe>()
                    .sortedByDescending { it.createdAt }
            } catch (e: Exception) {
                error = e.message?.take(100)
            }
            isLoading = false
        }
    }

    suspend fun loadFull(recipe: Recipe): FullRecipe {
        val ingredients = supabase.from("recipe_ingredients")
            .select { filter { eq("recipe_id", recipe.id) } }
            .decodeList<RecipeIngredient>()
            .sortedBy { it.sortOrder }
        val steps = supabase.from("recipe_steps")
            .select { filter { eq("recipe_id", recipe.id) } }
            .decodeList<RecipeStep>()
            .sortedBy { it.stepNumber }
        return FullRecipe(recipe, ingredients, steps)
    }

    LaunchedEffect(Unit) { load() }

    // Detail-Ansicht
    viewRecipe?.let { full ->
        RecipeDetailScreen(
            full = full,
            onBack = { viewRecipe = null },
            onEdit = {
                editRecipe = full
                viewRecipe = null
            },
            onDelete = {
                scope.launch {
                    try {
                        supabase.from("recipes").delete { filter { eq("id", full.recipe.id) } }
                        viewRecipe = null
                        load()
                    } catch (_: Exception) {}
                }
            }
        )
        return
    }

    // Edit-Dialog
    editRecipe?.let { full ->
        RecipeEditDialog(
            full = full,
            context = context,
            onDismiss = { editRecipe = null },
            onSave = { updatedFull ->
                scope.launch {
                    try {
                        saveFullRecipe(updatedFull, context, isNew = false)
                        editRecipe = null
                        load()
                    } catch (e: Exception) {
                        error = e.message?.take(100)
                    }
                }
            }
        )
        return
    }

    // Add-Sheet
    if (showAddSheet) {
        RecipeAddSheet(
            context = context,
            onDismiss = { showAddSheet = false },
            onSave = { full ->
                scope.launch {
                    try {
                        saveFullRecipe(full, context, isNew = true)
                        showAddSheet = false
                        load()
                    } catch (e: Exception) {
                        error = e.message?.take(100)
                    }
                }
            }
        )
        return
    }

    // Übersichts-UI
    val allTags = recipes.flatMap { it.tags ?: emptyList() }.distinct().sorted()
    val filtered = recipes.filter { r ->
        val matchSearch = searchQuery.isBlank() ||
            r.title.contains(searchQuery, ignoreCase = true) ||
            r.description?.contains(searchQuery, ignoreCase = true) == true
        val matchTag = selectedTag == null || r.tags?.contains(selectedTag) == true
        matchSearch && matchTag
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Suchzeile
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Rezept suchen...") },
                leadingIcon = { Icon(Icons.Default.Search, null) },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(Icons.Default.Clear, null)
                        }
                    }
                },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                shape = RoundedCornerShape(24.dp)
            )

            // Tag-Filter
            if (allTags.isNotEmpty()) {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(bottom = 8.dp)
                ) {
                    item {
                        FilterChip(
                            selected = selectedTag == null,
                            onClick = { selectedTag = null },
                            label = { Text("Alle") }
                        )
                    }
                    items(allTags) { tag ->
                        FilterChip(
                            selected = selectedTag == tag,
                            onClick = { selectedTag = if (selectedTag == tag) null else tag },
                            label = { Text(tag) }
                        )
                    }
                }
            }

            when {
                isLoading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
                error != null -> Column(
                    Modifier.fillMaxSize().padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(Icons.Default.WifiOff, null, Modifier.size(48.dp),
                        tint = MaterialTheme.colorScheme.error)
                    Spacer(Modifier.height(8.dp))
                    Text(error!!, color = MaterialTheme.colorScheme.error, fontSize = 13.sp)
                    Spacer(Modifier.height(12.dp))
                    Button(onClick = { load() }) { Text("Erneut versuchen") }
                }
                filtered.isEmpty() -> Column(
                    Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text("🍽️", fontSize = 56.sp)
                    Spacer(Modifier.height(8.dp))
                    Text("Noch keine Rezepte", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    Text("Füge dein erstes Rezept hinzu!",
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                else -> LazyColumn(
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(filtered, key = { it.id }) { recipe ->
                        RecipeCard(
                            recipe = recipe,
                            onClick = {
                                scope.launch {
                                    viewRecipe = loadFull(recipe)
                                }
                            }
                        )
                    }
                    item { Spacer(Modifier.height(80.dp)) }
                }
            }
        }

        // FAB
        FloatingActionButton(
            onClick = { showAddSheet = true },
            modifier = Modifier.align(Alignment.BottomEnd).padding(20.dp),
            containerColor = MaterialTheme.colorScheme.primary
        ) {
            Icon(Icons.Default.Add, null, tint = MaterialTheme.colorScheme.onPrimary)
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Rezept-Karte
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun RecipeCard(recipe: Recipe, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
            // Bild / Platzhalter
            if (!recipe.imageUrl.isNullOrBlank()) {
                AsyncImage(
                    model = recipe.imageUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.width(110.dp).fillMaxHeight()
                        .clip(RoundedCornerShape(topStart = 16.dp, bottomStart = 16.dp))
                )
            } else {
                Box(
                    modifier = Modifier.width(110.dp).fillMaxHeight()
                        .background(MaterialTheme.colorScheme.primaryContainer)
                        .clip(RoundedCornerShape(topStart = 16.dp, bottomStart = 16.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Text("🍽️", fontSize = 36.sp)
                }
            }

            Column(modifier = Modifier.weight(1f).padding(12.dp)) {
                Text(recipe.title, fontWeight = FontWeight.Bold, fontSize = 15.sp,
                    maxLines = 2, overflow = TextOverflow.Ellipsis)

                if (!recipe.description.isNullOrBlank()) {
                    Spacer(Modifier.height(4.dp))
                    Text(recipe.description, fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2, overflow = TextOverflow.Ellipsis)
                }

                Spacer(Modifier.height(6.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    if (recipe.prepTimeMin != null || recipe.cookTimeMin != null) {
                        val total = (recipe.prepTimeMin ?: 0) + (recipe.cookTimeMin ?: 0)
                        Icon(Icons.Default.Timer, null, Modifier.size(14.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("${total} Min", fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Icon(Icons.Default.People, null, Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("${recipe.servings}", fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)

                    if (recipe.sourceType == "screenshot") {
                        Icon(Icons.Default.PhotoCamera, null, Modifier.size(14.dp),
                            tint = MaterialTheme.colorScheme.primary)
                    }
                }

                // Tags
                if (!recipe.tags.isNullOrEmpty()) {
                    Spacer(Modifier.height(4.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        items(recipe.tags) { tag ->
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.secondaryContainer
                            ) {
                                Text(tag, fontSize = 11.sp,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                    color = MaterialTheme.colorScheme.onSecondaryContainer)
                            }
                        }
                    }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Detail-Ansicht (mit Zutaten-Abhaken)
// ─────────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecipeDetailScreen(
    full: FullRecipe,
    onBack: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val recipe = full.recipe
    var checkedIngredients by remember { mutableStateOf(setOf<String>()) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var servingsMultiplier by remember { mutableStateOf(1f) }
    val baseServings = recipe.servings.coerceAtLeast(1)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(recipe.title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, null)
                    }
                },
                actions = {
                    IconButton(onClick = onEdit) {
                        Icon(Icons.Default.Edit, null)
                    }
                    IconButton(onClick = { showDeleteDialog = true }) {
                        Icon(Icons.Default.Delete, null, tint = MaterialTheme.colorScheme.error)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    navigationIconContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    actionIconContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        }
    ) { padding ->
        LazyColumn(
            contentPadding = PaddingValues(bottom = 32.dp),
            modifier = Modifier.fillMaxSize().padding(padding)
        ) {
            // Bild
            if (!recipe.imageUrl.isNullOrBlank()) {
                item {
                    AsyncImage(
                        model = recipe.imageUrl,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxWidth().height(220.dp)
                    )
                }
            }

            // Meta-Info
            item {
                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                    if (!recipe.description.isNullOrBlank()) {
                        Text(recipe.description, fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.height(10.dp))
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        if ((recipe.prepTimeMin ?: 0) > 0) {
                            MetaChip("⏱ Vorbereitung", "${recipe.prepTimeMin} Min")
                        }
                        if ((recipe.cookTimeMin ?: 0) > 0) {
                            MetaChip("🔥 Kochen", "${recipe.cookTimeMin} Min")
                        }
                    }

                    if (!recipe.tags.isNullOrEmpty()) {
                        Spacer(Modifier.height(8.dp))
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            items(recipe.tags) { tag ->
                                AssistChip(onClick = {}, label = { Text(tag, fontSize = 12.sp) })
                            }
                        }
                    }
                }
                HorizontalDivider()
            }

            // Portionen-Scaler
            item {
                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()) {
                        Text("Zutaten", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                        Row(verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("Portionen:", fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                            IconButton(
                                onClick = {
                                    val newServings = (baseServings * servingsMultiplier - 1)
                                        .toInt().coerceAtLeast(1)
                                    servingsMultiplier = newServings.toFloat() / baseServings
                                },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(Icons.Default.Remove, null)
                            }
                            Text(
                                "${(baseServings * servingsMultiplier).toInt()}",
                                fontWeight = FontWeight.Bold, fontSize = 16.sp,
                                modifier = Modifier.widthIn(min = 24.dp)
                            )
                            IconButton(
                                onClick = {
                                    val newServings = (baseServings * servingsMultiplier + 1).toInt()
                                    servingsMultiplier = newServings.toFloat() / baseServings
                                },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(Icons.Default.Add, null)
                            }
                        }
                    }

                    // Alle abhaken Button
                    if (full.ingredients.isNotEmpty()) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End
                        ) {
                            TextButton(onClick = {
                                checkedIngredients = if (checkedIngredients.size == full.ingredients.size)
                                    emptySet()
                                else full.ingredients.map { it.id }.toSet()
                            }) {
                                Text(
                                    if (checkedIngredients.size == full.ingredients.size)
                                        "Alle zurücksetzen"
                                    else "Alle abhaken",
                                    fontSize = 12.sp
                                )
                            }
                        }
                    }
                }
            }

            // Zutaten-Liste
            items(full.ingredients) { ingredient ->
                val checked = ingredient.id in checkedIngredients
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            checkedIngredients = if (checked)
                                checkedIngredients - ingredient.id
                            else
                                checkedIngredients + ingredient.id
                        }
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = checked,
                        onCheckedChange = {
                            checkedIngredients = if (it)
                                checkedIngredients + ingredient.id
                            else
                                checkedIngredients - ingredient.id
                        }
                    )
                    Spacer(Modifier.width(8.dp))

                    // Menge skaliert
                    val scaledAmount = ingredient.amount?.toDoubleOrNull()?.let { amt ->
                        val scaled = amt * servingsMultiplier
                        if (scaled == scaled.toLong().toDouble()) scaled.toLong().toString()
                        else "%.1f".format(scaled)
                    } ?: ingredient.amount

                    val ingredientText = buildString {
                        if (!scaledAmount.isNullOrBlank()) append("$scaledAmount ")
                        if (!ingredient.unit.isNullOrBlank()) append("${ingredient.unit} ")
                        append(ingredient.name)
                    }

                    Text(
                        ingredientText,
                        fontSize = 15.sp,
                        textDecoration = if (checked) TextDecoration.LineThrough else null,
                        color = if (checked) MaterialTheme.colorScheme.onSurfaceVariant
                                else MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f)
                    )
                }
                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), thickness = 0.5.dp)
            }

            // Zubereitung
            if (full.steps.isNotEmpty()) {
                item {
                    Spacer(Modifier.height(8.dp))
                    HorizontalDivider()
                    Text(
                        "Zubereitung",
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
                    )
                }
                itemsIndexed(full.steps) { _, step ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .background(MaterialTheme.colorScheme.primary, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                "${step.stepNumber}",
                                color = MaterialTheme.colorScheme.onPrimary,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp
                            )
                        }
                        Spacer(Modifier.width(12.dp))
                        Text(step.description, fontSize = 15.sp,
                            modifier = Modifier.weight(1f).padding(top = 4.dp))
                    }
                }
            }
        }
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            icon = { Text("🗑️", fontSize = 28.sp) },
            title = { Text("Rezept löschen?") },
            text = { Text("\"${recipe.title}\" wird unwiderruflich gelöscht.") },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteDialog = false
                    onDelete()
                }) { Text("Löschen", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) { Text("Abbrechen") }
            }
        )
    }
}

@Composable
fun MetaChip(label: String, value: String) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.secondaryContainer
    ) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally) {
            Text(label, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSecondaryContainer)
            Text(value, fontSize = 13.sp, fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSecondaryContainer)
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Hinzufügen-Sheet: Auswahl Screenshot vs. Manuell
// ─────────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecipeAddSheet(
    context: Context,
    onDismiss: () -> Unit,
    onSave: (FullRecipe) -> Unit
) {
    var mode by remember { mutableStateOf<String?>(null) } // "screenshot" or "manual"

    if (mode == null) {
        Dialog(onDismissRequest = onDismiss) {
            Card(shape = RoundedCornerShape(20.dp)) {
                Column(modifier = Modifier.padding(24.dp)) {
                    Text("Rezept hinzufügen", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    Spacer(Modifier.height(20.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        // Screenshot
                        Card(
                            onClick = { mode = "screenshot" },
                            modifier = Modifier.weight(1f),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.primaryContainer)
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp).fillMaxWidth(),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text("📸", fontSize = 36.sp)
                                Spacer(Modifier.height(8.dp))
                                Text("Screenshot", fontWeight = FontWeight.Bold, fontSize = 14.sp,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer)
                                Text("Von Instagram hochladen", fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer)
                            }
                        }
                        // Manuell
                        Card(
                            onClick = { mode = "manual" },
                            modifier = Modifier.weight(1f),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.secondaryContainer)
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp).fillMaxWidth(),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text("✍️", fontSize = 36.sp)
                                Spacer(Modifier.height(8.dp))
                                Text("Manuell", fontWeight = FontWeight.Bold, fontSize = 14.sp,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer)
                                Text("Selbst eingeben", fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer)
                            }
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    TextButton(onClick = onDismiss, modifier = Modifier.align(Alignment.End)) {
                        Text("Abbrechen")
                    }
                }
            }
        }
    } else {
        RecipeEditDialog(
            full = null,
            context = context,
            sourceType = mode!!,
            onDismiss = onDismiss,
            onSave = onSave
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Bearbeiten / Neu-Eingabe Dialog
// ─────────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecipeEditDialog(
    full: FullRecipe?,           // null = neues Rezept
    context: Context,
    sourceType: String = full?.recipe?.sourceType ?: "manual",
    onDismiss: () -> Unit,
    onSave: (FullRecipe) -> Unit
) {
    val scope = rememberCoroutineScope()
    val isNew = full == null

    // Felder
    var title by remember { mutableStateOf(full?.recipe?.title ?: "") }
    var description by remember { mutableStateOf(full?.recipe?.description ?: "") }
    var servings by remember { mutableStateOf(full?.recipe?.servings?.toString() ?: "2") }
    var prepTime by remember { mutableStateOf(full?.recipe?.prepTimeMin?.toString() ?: "") }
    var cookTime by remember { mutableStateOf(full?.recipe?.cookTimeMin?.toString() ?: "") }
    var tagsText by remember { mutableStateOf(full?.recipe?.tags?.joinToString(", ") ?: "") }
    var imageUrl by remember { mutableStateOf(full?.recipe?.imageUrl ?: "") }

    var ingredients by remember {
        mutableStateOf(
            full?.ingredients?.map {
                EditIngredient(it.id, it.amount ?: "", it.unit ?: "", it.name)
            }?.toMutableList() ?: mutableListOf(EditIngredient())
        )
    }
    var steps by remember {
        mutableStateOf(
            full?.steps?.map { EditStep(it.id, it.description) }?.toMutableList()
                ?: mutableListOf(EditStep())
        )
    }

    // Bild-Upload
    var isUploading by remember { mutableStateOf(false) }
    var uploadError by remember { mutableStateOf<String?>(null) }

    val imagePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                isUploading = true
                uploadError = null
                try {
                    val bytes = context.contentResolver.openInputStream(uri)?.readBytes()
                        ?: throw Exception("Datei nicht lesbar")
                    val fileName = "recipe_${UUID.randomUUID()}.jpg"
                    supabase.storage.from("recipe-images")
                        .upload(fileName, bytes)
                    val publicUrl = supabase.storage.from("recipe-images")
                        .publicUrl(fileName)
                    imageUrl = publicUrl
                } catch (e: Exception) {
                    uploadError = "Upload fehlgeschlagen: ${e.message?.take(60)}"
                }
                isUploading = false
            }
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            shape = RoundedCornerShape(20.dp),
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .fillMaxHeight(0.95f)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Header
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.primaryContainer)
                        .padding(horizontal = 20.dp, vertical = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        if (isNew) "Neues Rezept" else "Rezept bearbeiten",
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        modifier = Modifier.weight(1f),
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer)
                    }
                }

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                        .padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    // Bild-Bereich
                    if (sourceType == "screenshot" || imageUrl.isNotBlank()) {
                        Column {
                            Text("Bild", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                            Spacer(Modifier.height(6.dp))
                            if (imageUrl.isNotBlank()) {
                                Box {
                                    AsyncImage(
                                        model = imageUrl,
                                        contentDescription = null,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(180.dp)
                                            .clip(RoundedCornerShape(12.dp))
                                    )
                                    OutlinedButton(
                                        onClick = { imagePicker.launch("image/*") },
                                        modifier = Modifier
                                            .align(Alignment.BottomEnd)
                                            .padding(8.dp)
                                    ) { Text("Ändern") }
                                }
                            } else {
                                OutlinedButton(
                                    onClick = { imagePicker.launch("image/*") },
                                    modifier = Modifier.fillMaxWidth().height(120.dp),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        if (isUploading) {
                                            CircularProgressIndicator(modifier = Modifier.size(24.dp))
                                        } else {
                                            Icon(Icons.Default.AddPhotoAlternate, null,
                                                modifier = Modifier.size(32.dp))
                                            Spacer(Modifier.height(4.dp))
                                            Text("Screenshot auswählen")
                                        }
                                    }
                                }
                            }
                            if (uploadError != null) {
                                Text(uploadError!!, color = MaterialTheme.colorScheme.error,
                                    fontSize = 12.sp)
                            }
                        }
                    }

                    // Grundinfo
                    OutlinedTextField(
                        value = title,
                        onValueChange = { title = it },
                        label = { Text("Titel *") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = description,
                        onValueChange = { description = it },
                        label = { Text("Beschreibung") },
                        maxLines = 3,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedTextField(
                            value = servings,
                            onValueChange = { servings = it.filter { c -> c.isDigit() } },
                            label = { Text("Portionen") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = prepTime,
                            onValueChange = { prepTime = it.filter { c -> c.isDigit() } },
                            label = { Text("Vorber. (Min)") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = cookTime,
                            onValueChange = { cookTime = it.filter { c -> c.isDigit() } },
                            label = { Text("Kochen (Min)") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                    }

                    OutlinedTextField(
                        value = tagsText,
                        onValueChange = { tagsText = it },
                        label = { Text("Tags (kommagetrennt)") },
                        placeholder = { Text("z.B. Pasta, Vegan, Schnell") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    // Zutaten
                    Text("Zutaten", fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                    ingredients.forEachIndexed { i, ing ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            OutlinedTextField(
                                value = ing.amount,
                                onValueChange = { v ->
                                    ingredients = ingredients.toMutableList().also { it[i] = ing.copy(amount = v) }
                                },
                                label = { Text("Menge") },
                                singleLine = true,
                                modifier = Modifier.width(72.dp)
                            )
                            OutlinedTextField(
                                value = ing.unit,
                                onValueChange = { v ->
                                    ingredients = ingredients.toMutableList().also { it[i] = ing.copy(unit = v) }
                                },
                                label = { Text("Einheit") },
                                singleLine = true,
                                modifier = Modifier.width(72.dp)
                            )
                            OutlinedTextField(
                                value = ing.name,
                                onValueChange = { v ->
                                    ingredients = ingredients.toMutableList().also { it[i] = ing.copy(name = v) }
                                },
                                label = { Text("Zutat") },
                                singleLine = true,
                                modifier = Modifier.weight(1f)
                            )
                            IconButton(
                                onClick = {
                                    ingredients = ingredients.toMutableList().also { it.removeAt(i) }
                                },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(Icons.Default.RemoveCircleOutline, null,
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(20.dp))
                            }
                        }
                    }
                    OutlinedButton(
                        onClick = { ingredients = ingredients.toMutableList().also { it.add(EditIngredient()) } },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Add, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Zutat hinzufügen")
                    }

                    // Schritte
                    Text("Zubereitung", fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                    steps.forEachIndexed { i, step ->
                        Row(
                            verticalAlignment = Alignment.Top,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .padding(top = 16.dp)
                                    .size(28.dp)
                                    .background(MaterialTheme.colorScheme.primary, CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Text("${i + 1}", color = MaterialTheme.colorScheme.onPrimary,
                                    fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                            OutlinedTextField(
                                value = step.description,
                                onValueChange = { v ->
                                    steps = steps.toMutableList().also { it[i] = step.copy(description = v) }
                                },
                                label = { Text("Schritt ${i + 1}") },
                                maxLines = 4,
                                modifier = Modifier.weight(1f)
                            )
                            IconButton(
                                onClick = { steps = steps.toMutableList().also { it.removeAt(i) } },
                                modifier = Modifier.size(36.dp).padding(top = 12.dp)
                            ) {
                                Icon(Icons.Default.RemoveCircleOutline, null,
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(20.dp))
                            }
                        }
                    }
                    OutlinedButton(
                        onClick = { steps = steps.toMutableList().also { it.add(EditStep()) } },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Add, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Schritt hinzufügen")
                    }
                }

                // Speichern
                HorizontalDivider()
                Row(
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp).fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) { Text("Abbrechen") }
                    Spacer(Modifier.width(8.dp))
                    Button(
                        onClick = {
                            val tags = tagsText.split(",")
                                .map { it.trim() }
                                .filter { it.isNotEmpty() }
                            val recipeId = full?.recipe?.id ?: UUID.randomUUID().toString()
                            val newRecipe = Recipe(
                                id = recipeId,
                                title = title.trim(),
                                description = description.trim().ifBlank { null },
                                sourceType = sourceType,
                                imageUrl = imageUrl.trim().ifBlank { null },
                                servings = servings.toIntOrNull() ?: 2,
                                prepTimeMin = prepTime.toIntOrNull(),
                                cookTimeMin = cookTime.toIntOrNull(),
                                tags = tags.ifEmpty { null }
                            )
                            val newIngredients = ingredients
                                .filter { it.name.isNotBlank() }
                                .mapIndexed { i, ing ->
                                    RecipeIngredient(
                                        recipeId = recipeId,
                                        name = ing.name.trim(),
                                        amount = ing.amount.trim().ifBlank { null },
                                        unit = ing.unit.trim().ifBlank { null },
                                        sortOrder = i
                                    )
                                }
                            val newSteps = steps
                                .filter { it.description.isNotBlank() }
                                .mapIndexed { i, step ->
                                    RecipeStep(
                                        recipeId = recipeId,
                                        stepNumber = i + 1,
                                        description = step.description.trim()
                                    )
                                }
                            onSave(FullRecipe(newRecipe, newIngredients, newSteps))
                        },
                        enabled = title.isNotBlank() && !isUploading
                    ) {
                        Text("Speichern")
                    }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Supabase Speicher-Logik
// ─────────────────────────────────────────────────────────────────────────────

suspend fun saveFullRecipe(full: FullRecipe, context: Context, isNew: Boolean) {
    val recipe = full.recipe
    if (isNew) {
        supabase.from("recipes").insert(recipe)
    } else {
        supabase.from("recipes").update(recipe) { filter { eq("id", recipe.id) } }
        supabase.from("recipe_ingredients").delete { filter { eq("recipe_id", recipe.id) } }
        supabase.from("recipe_steps").delete { filter { eq("recipe_id", recipe.id) } }
    }
    if (full.ingredients.isNotEmpty()) {
        supabase.from("recipe_ingredients").insert(full.ingredients)
    }
    if (full.steps.isNotEmpty()) {
        supabase.from("recipe_steps").insert(full.steps)
    }
}
