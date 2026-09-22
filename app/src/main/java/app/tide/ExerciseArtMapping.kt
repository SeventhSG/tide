package app.tide

import app.tide.core.data.db.Equipment
import app.tide.core.data.db.Muscle
import app.tide.core.design.ArtEquipment
import app.tide.core.design.ArtRegion

/**
 * Which drawn mark stands for which exercise.
 *
 * The mapping lives in `:app` rather than in `:core:design`, because the design
 * layer must not know about the database's enums. It takes the exercise's own
 * equipment and primary muscle, so the image is derived from the data rather
 * than assigned by hand for 1,300 rows.
 */
fun Equipment?.art(): ArtEquipment = when (this) {
    Equipment.Barbell, Equipment.EzBar, Equipment.TrapBar, Equipment.Smith -> ArtEquipment.Barbell
    Equipment.Dumbbell -> ArtEquipment.Dumbbell
    Equipment.Machine -> ArtEquipment.Machine
    Equipment.Cable -> ArtEquipment.Cable
    Equipment.Bodyweight -> ArtEquipment.Bodyweight
    Equipment.Kettlebell -> ArtEquipment.Kettlebell
    Equipment.Band -> ArtEquipment.Band
    Equipment.Other, null -> ArtEquipment.Other
}

fun Muscle?.region(): ArtRegion = when (this) {
    Muscle.Chest, Muscle.FrontDelts, Muscle.SideDelts, Muscle.Triceps -> ArtRegion.Push
    Muscle.Back, Muscle.Lats, Muscle.Traps, Muscle.RearDelts, Muscle.Biceps, Muscle.Forearms ->
        ArtRegion.Pull
    Muscle.Quads, Muscle.Hamstrings, Muscle.Glutes, Muscle.Calves,
    Muscle.Adductors, Muscle.Abductors -> ArtRegion.Legs
    Muscle.Abs, Muscle.Obliques, Muscle.LowerBack -> ArtRegion.Core
    Muscle.Neck, Muscle.FullBody, null -> ArtRegion.Other
}
