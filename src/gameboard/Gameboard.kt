package gameboard

import java.io.BufferedReader
import java.io.FileReader

class Gameboard(width: Int, height: Int, inputField: BufferedReader) {

    val field = Array(height) { Array(width) { ' ' } }

    private var stones: MutableMap<Point, Boolean> = mutableMapOf()
    private var lambdaStones: MutableMap<Point, Boolean> = mutableMapOf()
    private var robot = Point(-1, -1)
    private val beard = Point(-1, -1)

    init {
        var j = 0
        for (s in inputField.lines()) {
            for ((k, c) in s.toCharArray().withIndex()) {
                field[j][k] = c
                when (c) {
                    'R' -> robot.setNewPoint(k, j)
                    '*' -> stones[Point(k, j)] = false
                    '@' -> lambdaStones[Point(k, j)] = false
                    'W' -> beard.setNewPoint(k, j)
                }
            }
            j++
        }
    }

    private var score = 0
    private var growth = 15 // количество шагов через которое вырастет борода
    private var razor = 0
    private var flooding = 0    // скорость прибывания воды (количество шагов через которое вода поднимается на 1 уровень)
    private var waterLevel = 0   // уровень воды
    private var waterproof = 10 // сколько шагов без выныривания можно пройти
    private var numberOfSteps = 0
    private var collectedLambdas = 0
    private var isFalling = false // падают ща камни или не

    enum class State {
        LIVE,
        ABORTED,
        WON,
        DEAD;
    }

    enum class Move(val x: Int, val y: Int) {
        RIGHT(1, 0),
        LEFT(-1, 0),
        UP(0, 1),
        DOWN(0, -1);
    }

    private var state: State = State.LIVE

    fun act(commands: String) {
        if (state == State.LIVE) {
            when (commands) {
                "R" -> go(Move.RIGHT)
                "L" -> go(Move.LEFT)
                "U" -> go(Move.UP)
                "D" -> go(Move.DOWN)
                "S" -> shave()
                "A" -> abort()
                "W" -> waiting()
            }
        }
    }

    private fun go(move: Move) {
        numberOfSteps++
        if (isFalling) {
            val fallingStones = stones.containsValue(true)
            val fallingLambdaStones = lambdaStones.containsValue(true)
            falling(fallingStones, fallingLambdaStones)
        }
        if (flooding > 0 && numberOfSteps == flooding) { // если установлена скорость прибывания воды и
            // количество шагов равно ей, тогда поднять воду на 1 лвл и обнулить кол-во шагов
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
            if (stones.containsKey(Point(robot.y + 1, robot.x))) { // если НАД текущем положением робота камень,
                // то запускаем падение камня со след хода
                isFalling = true
                stones[Point(robot.y + 1, robot.x)] = true
            }
            if (lambdaStones.containsKey(Point(robot.y + 1, robot.x))) {
                isFalling = true
                lambdaStones[Point(robot.y + 1, robot.x)] = true
            }
            if (field[robot.y + move.y][robot.x + move.x] != '#' &&
                    field[robot.y + move.y][robot.x + move.x] != 'W' &&
                    field[robot.y + move.y][robot.x + move.x] != 'L') { // если след координата робота НЕ стена,
                // НЕ борода, НЕ закрытый лифт
                when (field[robot.y + move.y][robot.x + move.x]) {
                    '.' -> { // земля
                        field[robot.y + move.y][robot.x + move.x] = ' '
                        robot.setNewPoint(robot.x + move.x, robot.y + move.y)
                        score--
                    }
                    '\\' -> { // лямбда
                        field[robot.y + move.y][robot.x + move.x] = ' '
                        score += 24
                        robot.setNewPoint(robot.x + move.x, robot.y + move.y)
                        collectedLambdas++
                    }
                    '!' -> { // бритва
                        field[robot.y + move.y][robot.x + move.x] = ' '
                        robot.setNewPoint(robot.x + move.x, robot.y + move.y)
                        razor++
                        score--
                    }
                    'O' -> { // открытый лифт
                        robot.setNewPoint(robot.x + move.x, robot.y + move.y)
                        score += 50 * collectedLambdas
                        state = State.WON
                        gameover()
                    }
                    '*' -> { // передвинуть камень
                        if (field[robot.y + move.y][robot.x + 2 * move.x] == ' ' && (move == Move.RIGHT || move == Move.LEFT)) {
                            stones.remove(Point(robot.y + move.y, robot.x + move.x))
                            stones[Point(robot.y + move.y, robot.x + 2 * move.x)] = false
                            if (field[robot.y + move.y - 1][robot.x + 2 * move.x] == ' ') {
                                isFalling = true
                                stones[Point(robot.y + move.y, robot.x + 2 * move.x)] = true
                            }
                            robot.setNewPoint(robot.x + move.x, robot.y + move.y)
                        }
                        else {}
                    }
                    '@' -> {
                        if (field[robot.y + move.y][robot.x + 2 * move.x] == ' ' && (move == Move.RIGHT || move == Move.LEFT)) {
                            lambdaStones.remove(Point(robot.y + move.y, robot.x + move.x))
                            lambdaStones[Point(robot.y + move.y, robot.x + 2 * move.x)] = false
                            if (field[robot.y + move.y - 1][robot.x + 2 * move.x] == ' ') {
                                isFalling = true
                                lambdaStones[Point(robot.y + move.y, robot.x + 2 * move.x)] = true
                            }
                            robot.setNewPoint(robot.x + move.x, robot.y + move.y)
                        }
                        else {}
                    }
                    else -> {
                        robot.setNewPoint(robot.x + move.x, robot.y + move.y)
                        score--
                    }
                }
            }
        }
        if (waterproof < 0) { // утонул
            state = State.DEAD
            gameover()
        }
    }

    // Добавить трамплины

    private fun shave() {
        razor--
        // ??? Придумать как расти, чтобы понять как брить
    }

    private fun waiting() {
        score--
        numberOfSteps++
        //повторение - подумать
        if (isFalling) {
            val fallingStones = stones.containsValue(true)
            val fallingLambdaStones = lambdaStones.containsValue(true)
            falling(fallingStones, fallingLambdaStones)
        }
        if (flooding > 0 && numberOfSteps == flooding) {
            waterLevel++
            numberOfSteps = 0
        }
        if (waterLevel >= robot.y) {
            waterproof--
        }
        if (waterproof < 0) {
            state = State.DEAD
            gameover()
        }
    }

    private fun abort() {
        state = State.ABORTED
        score += 25 * collectedLambdas
        gameover()
    }

    private fun falling(fallingStones: Boolean, fallingLambdaStones: Boolean) { // chtobi dvigat kamni nado dumat kak kamen
        if (fallingStones) {
            for (key in stones.keys) { // проходим по всем ключам в камнях
                if (stones.getValue(key)) { // если по какому-то из ключей значение тру - падаем
                    if (field[key.y - 1][key.x] == ' ') key.setNewPoint(key.x, key.y - 1)
                    if (field[key.y - 1][key.x] == '.' || field[key.y - 1][key.x] == '#') stones[key] = false
                    if (field[key.y - 1][key.x] == '*' ||
                            field[key.y - 1][key.x] == '\\' ||
                            field[key.y - 1][key.x] == '@') {
                        if (field[key.y][key.x + 1] == ' ' &&
                                field[key.y - 1][key.x + 1] == ' ') key.setNewPoint(key.x + 1, key.y - 1)
                    }
                    if (field[key.y][key.x + 1] == 'R') state = State.DEAD // раздавило
                }
            }
        }
        if (fallingLambdaStones) {
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
        if (!fallingStones && !fallingLambdaStones) isFalling = false
    }

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