package tech.vauldex.dekita.data.mock

import tech.vauldex.dekita.domain.FulfillmentMode
import tech.vauldex.dekita.domain.Menu
import tech.vauldex.dekita.domain.MenuCategory
import tech.vauldex.dekita.domain.MenuItem
import tech.vauldex.dekita.domain.ModifierGroup
import tech.vauldex.dekita.domain.ModifierOption
import tech.vauldex.dekita.domain.Money

private fun yen(amount: Long) = Money.yen(amount)

private fun spiceLevel(groupId: String, vararg extra: ModifierOption) = ModifierGroup(
    id = groupId,
    name = "Spice level",
    minSelections = 1,
    maxSelections = 1,
    options = listOf(
        ModifierOption("$groupId-mild", "Mild"),
        ModifierOption("$groupId-medium", "Medium"),
        ModifierOption("$groupId-hot", "Hot"),
    ) + extra,
)

private val ramenToppings = ModifierGroup(
    id = "ramen-toppings",
    name = "Toppings",
    minSelections = 0,
    maxSelections = 3,
    options = listOf(
        ModifierOption("topping-egg", "Extra Egg", yen(120)),
        ModifierOption("topping-chashu", "Chashu", yen(250)),
        ModifierOption("topping-nori", "Nori", yen(80)),
        ModifierOption("topping-corn", "Corn", yen(100), isSoldOut = true),
    ),
)

private val massagePressure = ModifierGroup(
    id = "massage-pressure",
    name = "Pressure",
    minSelections = 1,
    maxSelections = 1,
    options = listOf(
        ModifierOption("pressure-soft", "Soft"),
        ModifierOption("pressure-medium", "Medium"),
        ModifierOption("pressure-firm", "Firm"),
    ),
)

private val massageAddOns = ModifierGroup(
    id = "massage-add-ons",
    name = "Add-ons",
    minSelections = 0,
    maxSelections = 1,
    options = listOf(
        ModifierOption("add-on-aroma", "Aroma oil", yen(500)),
        ModifierOption("add-on-hot-stone", "Hot stones", yen(800), isSoldOut = true),
    ),
)

private val ramenBarMenu = SeedStoreIds.RAMEN_BAR.let { store ->
    Menu(
        storeId = store,
        categories = listOf(
            MenuCategory("ramen", "Ramen"),
            MenuCategory("sides", "Sides"),
            MenuCategory("drinks", "Drinks"),
        ),
        items = listOf(
            MenuItem(
                id = "item-shoyu-ramen", storeId = store, categoryId = "ramen",
                name = "Shoyu Ramen", description = "Soy broth, chashu, egg, nori",
                basePrice = yen(980), modifierGroups = listOf(spiceLevel("ramen-spice"), ramenToppings),
            ),
            MenuItem(
                id = "item-tonkotsu-ramen", storeId = store, categoryId = "ramen",
                name = "Tonkotsu Ramen", description = "Rich pork broth, thin noodles",
                basePrice = yen(1050), isSoldOut = true,
                modifierGroups = listOf(spiceLevel("ramen-spice"), ramenToppings),
            ),
            MenuItem(
                id = "item-kaedama", storeId = store, categoryId = "ramen",
                name = "Kaedama (extra noodles)", description = "Served into your bowl at the table",
                basePrice = yen(150), availableModes = setOf(FulfillmentMode.IN_STORE),
            ),
            MenuItem(
                id = "item-gyoza", storeId = store, categoryId = "sides",
                name = "Gyoza (6 pcs)", description = "Pan-fried pork dumplings", basePrice = yen(450),
            ),
            MenuItem(
                id = "item-edamame", storeId = store, categoryId = "sides",
                name = "Edamame", description = "Salted soybeans", basePrice = yen(300),
            ),
            MenuItem(
                id = "item-ramen-kit", storeId = store, categoryId = "sides",
                name = "Take-home Ramen Kit", description = "Noodles and broth to cook at home",
                basePrice = yen(1200), availableModes = setOf(FulfillmentMode.PICKUP),
            ),
            MenuItem(
                id = "item-oolong-tea", storeId = store, categoryId = "drinks",
                name = "Iced Oolong Tea", description = "Unsweetened", basePrice = yen(200),
            ),
        ),
    )
}

private val martMenu = SeedStoreIds.MART.let { store ->
    Menu(
        storeId = store,
        categories = listOf(
            MenuCategory("onigiri", "Onigiri"),
            MenuCategory("bento", "Bento"),
            MenuCategory("drinks", "Drinks"),
        ),
        items = listOf(
            MenuItem(
                id = "item-onigiri-salmon", storeId = store, categoryId = "onigiri",
                name = "Salmon Onigiri", description = "Rice ball with grilled salmon", basePrice = yen(180),
            ),
            MenuItem(
                id = "item-onigiri-tuna", storeId = store, categoryId = "onigiri",
                name = "Tuna Mayo Onigiri", description = "Rice ball with tuna and mayo",
                basePrice = yen(170), isSoldOut = true,
            ),
            MenuItem(
                id = "item-karaage-bento", storeId = store, categoryId = "bento",
                name = "Karaage Bento", description = "Fried chicken, rice, pickles", basePrice = yen(550),
                modifierGroups = listOf(
                    ModifierGroup(
                        id = "bento-heat", name = "Warm it up?", minSelections = 1, maxSelections = 1,
                        options = listOf(
                            ModifierOption("heat-yes", "Warm"),
                            ModifierOption("heat-no", "Don't warm"),
                        ),
                    ),
                ),
            ),
            MenuItem(
                id = "item-green-tea", storeId = store, categoryId = "drinks",
                name = "Bottled Green Tea", description = "500 ml", basePrice = yen(150),
            ),
        ),
    )
}

private val massageStudioMenu = SeedStoreIds.MASSAGE_STUDIO.let { store ->
    Menu(
        storeId = store,
        categories = listOf(MenuCategory("massage", "Massage")),
        items = listOf(
            MenuItem(
                id = "item-shoulder-30", storeId = store, categoryId = "massage",
                name = "30-min Shoulder Massage", description = "Neck, shoulders and upper back",
                basePrice = yen(3000), modifierGroups = listOf(massagePressure, massageAddOns),
            ),
            MenuItem(
                id = "item-full-body-60", storeId = store, categoryId = "massage",
                name = "60-min Full Body Massage", description = "Head to toe",
                basePrice = yen(5500), modifierGroups = listOf(massagePressure, massageAddOns),
            ),
        ),
    )
}

private val curryStandMenu = SeedStoreIds.CURRY_STAND.let { store ->
    Menu(
        storeId = store,
        categories = listOf(MenuCategory("curry", "Curry")),
        items = listOf(
            MenuItem(
                id = "item-beef-curry", storeId = store, categoryId = "curry",
                name = "Beef Curry", description = "Slow-cooked beef, rice", basePrice = yen(850),
                modifierGroups = listOf(
                    spiceLevel("curry-spice", ModifierOption("curry-spice-extra-hot", "Extra hot", isSoldOut = true)),
                ),
            ),
            MenuItem(
                id = "item-katsu-curry", storeId = store, categoryId = "curry",
                name = "Katsu Curry", description = "Pork cutlet, rice", basePrice = yen(1000),
                modifierGroups = listOf(spiceLevel("curry-spice")),
            ),
        ),
    )
}

private val sobaHouseMenu = SeedStoreIds.SOBA_HOUSE.let { store ->
    Menu(
        storeId = store,
        categories = listOf(MenuCategory("soba", "Soba")),
        items = listOf(
            MenuItem(
                id = "item-zaru-soba", storeId = store, categoryId = "soba",
                name = "Zaru Soba", description = "Chilled buckwheat noodles", basePrice = yen(800),
            ),
            MenuItem(
                id = "item-tempura-soba", storeId = store, categoryId = "soba",
                name = "Tempura Soba", description = "Hot soba with shrimp tempura", basePrice = yen(1200),
            ),
        ),
    )
}

private val bentoKitchenMenu = SeedStoreIds.BENTO_KITCHEN.let { store ->
    Menu(
        storeId = store,
        categories = listOf(MenuCategory("bento", "Bento")),
        items = listOf(
            MenuItem(
                id = "item-salmon-bento", storeId = store, categoryId = "bento",
                name = "Grilled Salmon Bento", description = "Salmon, rice, tamagoyaki", basePrice = yen(900),
            ),
            MenuItem(
                id = "item-hamburg-bento", storeId = store, categoryId = "bento",
                name = "Hamburg Bento", description = "Beef patty, demi-glace, rice", basePrice = yen(950),
            ),
        ),
    )
}

private val udonHallMenu = SeedStoreIds.UDON_HALL.let { store ->
    Menu(
        storeId = store,
        categories = listOf(MenuCategory("udon", "Udon")),
        items = listOf(
            MenuItem(
                id = "item-kake-udon", storeId = store, categoryId = "udon",
                name = "Kake Udon", description = "Thick noodles in hot dashi", basePrice = yen(500),
            ),
            MenuItem(
                id = "item-kitsune-udon", storeId = store, categoryId = "udon",
                name = "Kitsune Udon", description = "With sweet fried tofu", basePrice = yen(650),
            ),
        ),
    )
}

internal val seedMenus: Map<String, Menu> = listOf(
    ramenBarMenu,
    martMenu,
    massageStudioMenu,
    curryStandMenu,
    sobaHouseMenu,
    bentoKitchenMenu,
    udonHallMenu,
).associateBy { it.storeId }

internal fun seedItem(storeId: String, itemId: String): MenuItem =
    seedMenus.getValue(storeId).items.first { it.id == itemId }

internal fun MenuItem.seedOption(optionId: String): ModifierOption =
    modifierGroups.flatMap { it.options }.first { it.id == optionId }
