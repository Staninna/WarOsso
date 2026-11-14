package io.github.staninna.warosso.domain

/**
 * The BAG numbers from [from] to [to], both included, in the typed direction. `169 → 151` counts
 * down. Only numbers that exist in [bagNumbers] are kept, so the step is whatever BAG has.
 */
fun fillRange(from: Int, to: Int, bagNumbers: Collection<Int>): List<Int> {
    val between = bagNumbers.filter { it in minOf(from, to)..maxOf(from, to) }
    return if (from <= to) between.sorted() else between.sortedDescending()
}

/**
 * Splits a building's numbers into runs that probably share a floor. A new run starts where the
 * jump to the next number is more than twice the usual jump. In this complex a building reads
 * like 9, 11, … 17 | 27, 29, … 35 | 45, … 53: one run per floor.
 */
fun suggestRuns(numbers: Collection<Int>): List<List<Int>> {
    val sorted = numbers.sorted().distinct()
    if (sorted.size < 2) return listOfNotNull(sorted.takeIf { it.isNotEmpty() })

    val gaps = sorted.zipWithNext { a, b -> b - a }
    val usualGap = gaps.sorted()[(gaps.size - 1) / 2]

    val runs = mutableListOf(mutableListOf(sorted.first()))
    sorted.zipWithNext { previous, next ->
        if (next - previous > 2 * usualGap) runs += mutableListOf<Int>()
        runs.last() += next
    }
    return runs
}

/**
 * Floors to propose for an entrance, ground floor first. [numbersByPart] holds the building's
 * numbers per OSM outline. Each outline is split into runs, and floor `n` is every outline's
 * `n`-th run together: a block mapped as two halves reads 1-7 | 19-25 | 37-43 on one side and
 * 9-17 | 27-35 | 45-53 on the other, which makes floors 1-17, 19-35 and 37-53.
 *
 * Runs whose doors the entrance already has are left out, and a proposal never reuses a floor
 * number the entrance already has.
 */
fun suggestFloors(numbersByPart: Collection<Collection<Int>>, existing: List<FloorDoors>): List<FloorDoors> {
    val runsPerPart = numbersByPart.map(::suggestRuns)
    val floorCount = runsPerPart.maxOfOrNull { it.size } ?: 0
    val usedDoors = existing.flatMapTo(HashSet()) { it.doors }
    val usedFloors = existing.mapTo(HashSet()) { it.floor }
    return (0 until floorCount)
        .map { floor -> FloorDoors(floor, runsPerPart.flatMap { it.getOrElse(floor) { emptyList() } }.sorted()) }
        .filter { it.floor !in usedFloors && it.doors.none(usedDoors::contains) }
}
