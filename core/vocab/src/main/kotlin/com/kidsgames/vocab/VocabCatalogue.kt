package com.kidsgames.vocab

/**
 * The bundled picture-and-word catalogue shared by :games:whatisit and
 * :games:talktime. At least 100 items across the twelve sectors, at least
 * six per sector, so no level is dominated by one category.
 *
 * [tier] drives level selection: tier 1 holds words a 4 year old already
 * names (dog, banana, car); tier 5 holds words a 6 year old is still
 * acquiring (helicopter, thermometer, mechanic).
 *
 * Real words, real everyday vocabulary. The image/audio resource ids are
 * placeholders — see PlaceholderAssets.kt — because no artwork or recorded
 * audio exists yet.
 */
object VocabCatalogue {

    private fun item(id: String, word: String, sector: Sector, tier: Int) = VocabItem(
        id = id,
        image = PlaceholderAssets.nextImage(),
        audio = PlaceholderAssets.nextAudio(),
        word = word,
        sector = sector,
        tier = tier,
    )

    val items: List<VocabItem> = listOf(
        // ANIMALS
        item("animal_dog", "dog", Sector.ANIMALS, 1),
        item("animal_cat", "cat", Sector.ANIMALS, 1),
        item("animal_fish", "fish", Sector.ANIMALS, 1),
        item("animal_bird", "bird", Sector.ANIMALS, 1),
        item("animal_cow", "cow", Sector.ANIMALS, 2),
        item("animal_duck", "duck", Sector.ANIMALS, 2),
        item("animal_horse", "horse", Sector.ANIMALS, 2),
        item("animal_rabbit", "rabbit", Sector.ANIMALS, 2),
        item("animal_lion", "lion", Sector.ANIMALS, 3),
        item("animal_elephant", "elephant", Sector.ANIMALS, 3),
        item("animal_monkey", "monkey", Sector.ANIMALS, 3),
        item("animal_bear", "bear", Sector.ANIMALS, 3),
        item("animal_owl", "owl", Sector.ANIMALS, 4),
        item("animal_penguin", "penguin", Sector.ANIMALS, 4),
        item("animal_octopus", "octopus", Sector.ANIMALS, 5),
        item("animal_chameleon", "chameleon", Sector.ANIMALS, 5),

        // FRUITS
        item("fruit_banana", "banana", Sector.FRUITS, 1),
        item("fruit_apple", "apple", Sector.FRUITS, 1),
        item("fruit_orange", "orange", Sector.FRUITS, 1),
        item("fruit_grape", "grape", Sector.FRUITS, 2),
        item("fruit_strawberry", "strawberry", Sector.FRUITS, 2),
        item("fruit_watermelon", "watermelon", Sector.FRUITS, 2),
        item("fruit_pear", "pear", Sector.FRUITS, 2),
        item("fruit_pineapple", "pineapple", Sector.FRUITS, 3),
        item("fruit_mango", "mango", Sector.FRUITS, 3),
        item("fruit_kiwi", "kiwi", Sector.FRUITS, 4),
        item("fruit_papaya", "papaya", Sector.FRUITS, 5),
        item("fruit_fig", "fig", Sector.FRUITS, 5),

        // VEGETABLES
        item("veg_carrot", "carrot", Sector.VEGETABLES, 1),
        item("veg_potato", "potato", Sector.VEGETABLES, 1),
        item("veg_tomato", "tomato", Sector.VEGETABLES, 1),
        item("veg_pea", "pea", Sector.VEGETABLES, 2),
        item("veg_corn", "corn", Sector.VEGETABLES, 2),
        item("veg_onion", "onion", Sector.VEGETABLES, 2),
        item("veg_broccoli", "broccoli", Sector.VEGETABLES, 3),
        item("veg_cucumber", "cucumber", Sector.VEGETABLES, 3),
        item("veg_pumpkin", "pumpkin", Sector.VEGETABLES, 3),
        item("veg_eggplant", "eggplant", Sector.VEGETABLES, 4),
        item("veg_cauliflower", "cauliflower", Sector.VEGETABLES, 4),
        item("veg_asparagus", "asparagus", Sector.VEGETABLES, 5),

        // VEHICLES
        item("vehicle_car", "car", Sector.VEHICLES, 1),
        item("vehicle_bus", "bus", Sector.VEHICLES, 1),
        item("vehicle_bike", "bike", Sector.VEHICLES, 1),
        item("vehicle_train", "train", Sector.VEHICLES, 2),
        item("vehicle_boat", "boat", Sector.VEHICLES, 2),
        item("vehicle_plane", "plane", Sector.VEHICLES, 2),
        item("vehicle_truck", "truck", Sector.VEHICLES, 2),
        item("vehicle_tractor", "tractor", Sector.VEHICLES, 3),
        item("vehicle_ambulance", "ambulance", Sector.VEHICLES, 3),
        item("vehicle_helicopter", "helicopter", Sector.VEHICLES, 4),
        item("vehicle_submarine", "submarine", Sector.VEHICLES, 5),
        item("vehicle_rocket", "rocket", Sector.VEHICLES, 4),

        // BODY
        item("body_hand", "hand", Sector.BODY, 1),
        item("body_foot", "foot", Sector.BODY, 1),
        item("body_nose", "nose", Sector.BODY, 1),
        item("body_eye", "eye", Sector.BODY, 1),
        item("body_ear", "ear", Sector.BODY, 2),
        item("body_mouth", "mouth", Sector.BODY, 2),
        item("body_hair", "hair", Sector.BODY, 2),
        item("body_finger", "finger", Sector.BODY, 3),
        item("body_shoulder", "shoulder", Sector.BODY, 3),
        item("body_elbow", "elbow", Sector.BODY, 4),
        item("body_knee", "knee", Sector.BODY, 4),
        item("body_eyebrow", "eyebrow", Sector.BODY, 5),

        // CLOTHES
        item("clothes_hat", "hat", Sector.CLOTHES, 1),
        item("clothes_shoe", "shoe", Sector.CLOTHES, 1),
        item("clothes_sock", "sock", Sector.CLOTHES, 1),
        item("clothes_shirt", "shirt", Sector.CLOTHES, 2),
        item("clothes_pants", "pants", Sector.CLOTHES, 2),
        item("clothes_jacket", "jacket", Sector.CLOTHES, 2),
        item("clothes_dress", "dress", Sector.CLOTHES, 3),
        item("clothes_glove", "glove", Sector.CLOTHES, 3),
        item("clothes_scarf", "scarf", Sector.CLOTHES, 4),
        item("clothes_pajamas", "pajamas", Sector.CLOTHES, 4),
        item("clothes_raincoat", "raincoat", Sector.CLOTHES, 5),

        // HOUSEHOLD
        item("house_cup", "cup", Sector.HOUSEHOLD, 1),
        item("house_spoon", "spoon", Sector.HOUSEHOLD, 1),
        item("house_chair", "chair", Sector.HOUSEHOLD, 1),
        item("house_table", "table", Sector.HOUSEHOLD, 1),
        item("house_bed", "bed", Sector.HOUSEHOLD, 2),
        item("house_door", "door", Sector.HOUSEHOLD, 2),
        item("house_window", "window", Sector.HOUSEHOLD, 2),
        item("house_lamp", "lamp", Sector.HOUSEHOLD, 3),
        item("house_pillow", "pillow", Sector.HOUSEHOLD, 3),
        item("house_blanket", "blanket", Sector.HOUSEHOLD, 3),
        item("house_broom", "broom", Sector.HOUSEHOLD, 4),
        item("house_thermometer", "thermometer", Sector.HOUSEHOLD, 5),

        // FOOD
        item("food_bread", "bread", Sector.FOOD, 1),
        item("food_milk", "milk", Sector.FOOD, 1),
        item("food_egg", "egg", Sector.FOOD, 1),
        item("food_cheese", "cheese", Sector.FOOD, 2),
        item("food_rice", "rice", Sector.FOOD, 2),
        item("food_soup", "soup", Sector.FOOD, 2),
        item("food_cookie", "cookie", Sector.FOOD, 2),
        item("food_pizza", "pizza", Sector.FOOD, 3),
        item("food_pancake", "pancake", Sector.FOOD, 3),
        item("food_sandwich", "sandwich", Sector.FOOD, 4),
        item("food_yogurt", "yogurt", Sector.FOOD, 4),
        item("food_noodles", "noodles", Sector.FOOD, 5),

        // NATURE
        item("nature_sun", "sun", Sector.NATURE, 1),
        item("nature_moon", "moon", Sector.NATURE, 1),
        item("nature_tree", "tree", Sector.NATURE, 1),
        item("nature_flower", "flower", Sector.NATURE, 1),
        item("nature_star", "star", Sector.NATURE, 2),
        item("nature_cloud", "cloud", Sector.NATURE, 2),
        item("nature_rain", "rain", Sector.NATURE, 2),
        item("nature_snow", "snow", Sector.NATURE, 3),
        item("nature_mountain", "mountain", Sector.NATURE, 3),
        item("nature_river", "river", Sector.NATURE, 3),
        item("nature_rainbow", "rainbow", Sector.NATURE, 4),
        item("nature_volcano", "volcano", Sector.NATURE, 5),

        // JOBS
        item("job_doctor", "doctor", Sector.JOBS, 2),
        item("job_teacher", "teacher", Sector.JOBS, 2),
        item("job_firefighter", "firefighter", Sector.JOBS, 2),
        item("job_police_officer", "police officer", Sector.JOBS, 3),
        item("job_chef", "chef", Sector.JOBS, 3),
        item("job_farmer", "farmer", Sector.JOBS, 3),
        item("job_pilot", "pilot", Sector.JOBS, 4),
        item("job_mechanic", "mechanic", Sector.JOBS, 5),
        item("job_dentist", "dentist", Sector.JOBS, 4),
        item("job_vet", "vet", Sector.JOBS, 4),
        item("job_astronaut", "astronaut", Sector.JOBS, 3),

        // INSTRUMENTS
        item("inst_drum", "drum", Sector.INSTRUMENTS, 1),
        item("inst_piano", "piano", Sector.INSTRUMENTS, 2),
        item("inst_guitar", "guitar", Sector.INSTRUMENTS, 2),
        item("inst_trumpet", "trumpet", Sector.INSTRUMENTS, 3),
        item("inst_violin", "violin", Sector.INSTRUMENTS, 3),
        item("inst_flute", "flute", Sector.INSTRUMENTS, 4),
        item("inst_xylophone", "xylophone", Sector.INSTRUMENTS, 4),
        item("inst_tambourine", "tambourine", Sector.INSTRUMENTS, 3),
        item("inst_harp", "harp", Sector.INSTRUMENTS, 5),
        item("inst_accordion", "accordion", Sector.INSTRUMENTS, 5),
        item("inst_saxophone", "saxophone", Sector.INSTRUMENTS, 5),

        // SPORTS
        item("sport_ball", "ball", Sector.SPORTS, 1),
        item("sport_swimming", "swimming", Sector.SPORTS, 2),
        item("sport_soccer", "soccer", Sector.SPORTS, 2),
        item("sport_running", "running", Sector.SPORTS, 1),
        item("sport_bicycle_race", "bike race", Sector.SPORTS, 3),
        item("sport_basketball", "basketball", Sector.SPORTS, 2),
        item("sport_skating", "skating", Sector.SPORTS, 3),
        item("sport_tennis", "tennis", Sector.SPORTS, 3),
        item("sport_gymnastics", "gymnastics", Sector.SPORTS, 4),
        item("sport_baseball", "baseball", Sector.SPORTS, 4),
        item("sport_hockey", "hockey", Sector.SPORTS, 5),
    )

    fun bySector(sector: Sector): List<VocabItem> = items.filter { it.sector == sector }

    /**
     * Level 1 draws only tier-1 items; each subsequent level admits higher
     * tiers on top, so the pool widens rather than the task tightening.
     */
    fun forLevel(level: Int): List<VocabItem> = items.filter { it.tier <= level.coerceIn(1, 5) }
}
