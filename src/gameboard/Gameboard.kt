package gameboard

import java.io.BufferedReader
import java.io.FileReader

class Gameboard(width : Int, height : Int, inputField : BufferedReader) {

    val field = Array(height) {Array(width) {' '} }

    init {
        var j = 0
        for (s in inputField.lines()) {
            var k = 0
            for (c in s.toCharArray()) {
                field[j][k] = c
                k++
            }
            j++
        }
    }

    //Must be defined from input stream
    var highscore = 0
    var growth = 0
    var flooding = 0
    var water = 0

    fun act(commands : String) {
        TODO()
    }
}

fun main(args: Array<String>) {
    val gbrd = Gameboard(6, 6, BufferedReader(FileReader("maps/map1")))
    println(gbrd.field)
}