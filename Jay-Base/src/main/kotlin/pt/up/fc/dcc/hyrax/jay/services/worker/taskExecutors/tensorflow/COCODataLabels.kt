/*
 * Copyright (c) 2021 University of Porto/Faculty of Sciences (UP/FCUP).
 * 
 * Author: Joaquim Silva
 *
 * This program is free software: you can redistribute it and/or modify it under the terms of the GNU General Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later version.
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License for more details.
 * You should have received a copy of the GNU General Public License along with this program. If not, see <https://www.gnu.org/licenses/>.
 *
 */

package pt.up.fc.dcc.hyrax.jay.services.worker.taskExecutors.tensorflow

object COCODataLabels{
    private val classToLabel : HashMap<Int, String> = hashMapOf(
        1 to "person",
        2 to "bicycle",
        3 to "car",
        4 to "motorcycle",
        5 to "airplane",
        6 to "bus",
        7 to "train",
        8 to "truck",
        9 to "boat",
        10 to "traffic light",
        11 to "fire hydrant",
        13 to "stop sign",
        14 to "parking meter",
        15 to "bench",
        16 to "bird",
        17 to "cat",
        18 to "dog",
        19 to "horse",
        20 to "sheep",
        21 to "cow",
        22 to "elephant",
        23 to "bear",
        24 to "zebra",
        25 to "giraffe",
        27 to "backpack",
        28 to "umbrella",
        31 to "handbag",
        32 to "tie",
        33 to "suitcase",
        34 to "frisbee",
        35 to "skis",
        36 to "snowboard",
        37 to "sports ball",
        38 to "kite",
        39 to "baseball bat",
        40 to "baseball glove",
        41 to "skateboard",
        42 to "surfboard",
        43 to "tennis racket",
        44 to "bottle",
        46 to "wine glass",
        47 to "cup",
        48 to "fork",
        49 to "knife",
        50 to "spoon",
        51 to "bowl",
        52 to "banana",
        53 to "apple",
        54 to "sandwich",
        55 to "orange",
        56 to "broccoli",
        57 to "carrot",
        58 to "hot dog",
        59 to "pizza",
        60 to "donut",
        61 to "cake",
        62 to "chair",
        63 to "couch",
        64 to "potted plant",
        65 to "bed",
        67 to "dining table",
        70 to "toilet",
        72 to "tv",
        73 to "laptop",
        74 to "mouse",
        75 to "remote",
        76 to "keyboard",
        77 to "cell phone",
        78 to "microwave",
        79 to "oven",
        80 to "toaster",
        81 to "sink",
        82 to "refrigerator",
        84 to "book",
        85 to "clock",
        86 to "vase",
            87 to "scissors",
            88 to "teddy bear",
            89 to "hair drier",
            90 to "toothbrush"
    )

    fun label(classId: Int): String {
        if (classId in classToLabel) return classToLabel[classId]!!
        return ""
    }

    fun classId(label: String): Int {
        if (label in classToLabel.values)
            classToLabel.forEach { (i, s) -> if (s == label) return i }
        return 0
    }
}