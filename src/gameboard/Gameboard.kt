package gameboard

import java.io.BufferedReader
import java.io.FileReader

class Gameboard(width: Int, height: Int, inputField: BufferedReader) {

    val field = Array(height) { Array(width) { ' ' } }

    var stones: MutableMap<Point, Boolean> = mutableMapOf()
    var lambdaStones: MutableMap<Point, Boolean> = mutableMapOf()
    var robot = Point(-1, -1)

    init {
        var j = 0
        for (s in inputField.lines()) {
            var k = 0
            for (c in s.toCharArray()) {
                field[j][k] = c
                when (c) {
                    'R' -> robot.setNewPoint(k, j)
                    '*' -> stones.put(Point(k, j), false)
                    '@' -> lambdaStones.put(Point(k, j), false)
                }
                k++
            }
            j++
        }
    }

    var score = 0
    var growth = 0
    var razor = 0
    var flooding = 0    // скорость прибывания воды (количество шагов через которое вода поднимается на 1 уровень)
    var waterLevel = 0   // уровень воды
    var waterproof = 10 // сколько шагов без выныривания можно пройти
    var numberOfSteps = 0
    var collectedLambdas = 0
    var stoneFalling = false

    enum class State {
        LIVE,
        ABORTED,
        WON,
        DEAD;
    }

    var state: State = State.LIVE

    fun act(commands: String) {
        if (state == State.LIVE) {
            when (commands) {
                "R" -> goRight()
                "L" -> goLeft()
                "U" -> goUp()
                "D" -> goDown()
                "S" -> shave()
                "A" -> abort()
                "W" -> waiting()
            }
        }
    }

    private fun goRight() {
        numberOfSteps++
        if (stoneFalling) { // chtobi dvigat kamni nado dumat kak kamen
            // ДУМАЮ МОЖНО ОБЪЕДИНИТЬ ЭТО А МОЖЕТ И НЕ
            for (key in stones.keys) { // проходим по всем ключам в камнях
                if (stones.getValue(key)) { // если по какому-то из ключей значение тру - падаем
                    if (field[key.y - 1][key.x] == ' ') key.setNewPoint(key.x, key.y - 1)
                    if (field[key.y - 1][key.x] == '.' || field[key.y - 1][key.x] == '#') stones.set(key, false)
                    if (field[key.y - 1][key.x] == '*' || field[key.y - 1][key.x] == '\\' || field[key.y - 1][key.x] == '@') {
                        if (field[key.y][key.x + 1] == ' ' && field[key.y - 1][key.x + 1] == ' ') key.setNewPoint(key.x + 1, key.y - 1)
                    }
                    if (field[key.y][key.x + 1] == 'R') state = State.DEAD
                }
            }
            for (key in lambdaStones.keys) {
                if (lambdaStones.getValue(key)) {
                    if (field[key.y - 1][key.x] == ' ') key.setNewPoint(key.x, key.y - 1)
                    if (field[key.y - 1][key.x] == '.' ||
                            field[key.y - 1][key.x] == '#' ||
                            field[key.y - 1][key.x] == '*' ||
                            field[key.y - 1][key.x] == '\\' ||
                            field[key.y - 1][key.x] == '@') {
                        lambdaStones.remove(key) // разбился - делит
                        field[key.y - 1][key.x] = '\\' // сделать там лямбду
                    }
                    if (field[key.y][key.x + 1] == 'R') state = State.DEAD
                }
            }
        }
        if (flooding > 0 && numberOfSteps == flooding) { // если установлена скорость прибывания воды и количество шагов равно ей, тогда поднять воду на 1 лвл и обнулить кол-во шагов
            waterLevel++
            numberOfSteps = 0
        }
        if (waterLevel >= robot.y) { // если робот в воде, уменьшаем вотерпруф
            waterproof--
        }
        if (waterLevel < robot.y) { // если вынырнул - обновляем
            waterproof = 10
        }
        if (waterproof >= 0) {
            if (stones.containsKey(Point(robot.y + 1, robot.x)) || lambdaStones.containsKey(Point(robot.y + 1, robot.x))) { // если НАД текущем положением робота камень, то запускаем падение камня со след хода
                stoneFalling = true
                stones[Point(robot.y + 1, robot.x)] = true
            }
            if (field[robot.y][robot.x + 1] != '#' && field[robot.y][robot.x + 1] != '*' && field[robot.y][robot.x + 1] != 'W' && field[robot.y][robot.x + 1] != 'L') { // если след координата робота НЕ стена, НЕ камень, НЕ борода, НЕ закрытый лифт
                when (field[robot.y][robot.x + 1]) {
                    '.' -> { // земля
                        field[robot.y][robot.x + 1] = ' '
                        robot.setNewPoint(robot.x + 1, robot.y)
                        score--
                    }
                    '\\' -> { // лямбда
                        field[robot.y][robot.x + 1] = ' '
                        robot.setNewPoint(robot.x + 1, robot.y)
                        collectedLambdas++
                        score += 24
                    }
                    '!' -> { // бритва
                        field[robot.y][robot.x + 1] = ' '
                        robot.setNewPoint(robot.x + 1, robot.y)
                        razor++
                        score--
                    }
                    'O' -> { // открытый лифт
                        robot.setNewPoint(robot.x + 1, robot.y)
                        score += 50 * collectedLambdas
                        state = State.WON
                        gameover()
                    }
                    else -> {
                        robot.setNewPoint(robot.x + 1, robot.y)
                        score--
                    }
                }
            }
            if (field[robot.y][robot.x + 1] == '*' && field[robot.y][robot.x + 2] == ' ') { // двигаем камень
                //stoneMoveRight
                robot.setNewPoint(robot.x + 1, robot.y)
            }
        }
        if (waterproof < 0) {
            state = State.DEAD
            gameover()
        }
    }

    // ЛЕВО НИЗ ВЕРХ ТОЖЕ САМОЕ ЧТО ПРАВО НАДО ОБЪЕДИНИТЬ Я ПОДУМАЮ
    private fun goLeft() {
        TODO()
    }

    private fun goUp() {
        TODO()
    }

    private fun goDown() {
        TODO()
    }

    private fun shave() {
        TODO()
    }

    private fun waiting() {
        score--
    }

    private fun abort() {
        state = State.ABORTED
        score += 25 * collectedLambdas
        gameover()
    }

    //private fun stoneFalling(state : Boolean, startY : Int, startX : Int) {
    //    TODO()
    //}
    private fun gameover() {
        println(score)
    }
}

data class Point(var x: Int, var y: Int) {

    fun setNewPoint(newX: Int, newY: Int) {
        x = newX
        y = newY
    }

}

fun main(args: Array<String>) {
    val gbrd = Gameboard(6, 6, BufferedReader(FileReader("maps/map1")))
    gbrd.act("R")
    println(gbrd.field)
}