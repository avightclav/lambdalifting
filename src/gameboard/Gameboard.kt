package gameboard

import java.io.BufferedReader
import java.io.FileReader

class Gameboard(width: Int, height: Int, inputField: BufferedReader) {

    val field = Array(height) { Array(width) { ' ' } }

    private var stones: MutableMap<Point, Boolean> = mutableMapOf()
    private var lambdaStones: MutableMap<Point, Boolean> = mutableMapOf()
    private lateinit var trampolinesArray: ArrayList<Trampoline>
    private lateinit var trampolines: HashMap<Trampoline, Trampoline>
    private var robot = Point(-1, -1)
    private val beard = Point(-1, -1)

    init {
        var j = height - 1
        for (s in inputField.lines()) { //поменять систему координат
            for ((k, c) in s.toCharArray().withIndex()) {
                field[j][k] = c
                when (c) {
                    'R' -> robot.setNewPoint(j, k)
                    '*' -> stones[Point(j, k)] = false
                    '@' -> lambdaStones[Point(j, k)] = false
                    'W' -> beard.setNewPoint(j, k)
                    '1', '2', '3', '4', '5', '6', '7', '8', '9' -> trampolinesArray.add(Trampoline(c, Point(j, k)))
                    'A', 'B', 'C', 'D', 'E', 'F', 'G', 'H', 'I' -> trampolinesArray.add(Trampoline(c, Point(j, k)))
                }
            }
            j--
        }
    }

    private var score = 0
    private var growth = 25 // количество шагов через которое вырастет борода
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

    enum class Move(val y: Int, val x: Int) {
        RIGHT(0, 1),
        LEFT(0, -1),
        UP(1, 0),
        DOWN(-1, 0);
    }

    private var state: State = State.LIVE

    fun act(commands: String) {
        for (command in commands) {
            if (state == State.LIVE) {
                when (command) {
                    'R' -> go(Move.RIGHT)
                    'L' -> go(Move.LEFT)
                    'U' -> go(Move.UP)
                    'D' -> go(Move.DOWN)
                    'S' -> shave()
                    'A' -> abort()
                    'W' -> waiting()
                }
            }
        }
    }

    private fun go(move: Move) {
        updateIndicators()
        if (waterproof >= 0) {
            if (stones.containsKey(Point(robot.y + 1, robot.x)) && move != Move.UP) { // если НАД текущем положением робота камень,
                // то запускаем падение камня со след хода
                isFalling = true
                stones[Point(robot.y + 1, robot.x)] = true
                falling(true, false)
            }
            if (lambdaStones.containsKey(Point(robot.y + 1, robot.x)) && move != Move.UP) {
                isFalling = true
                lambdaStones[Point(robot.y + 1, robot.x)] = true
                falling(false, true)
            }
            val yPoint = robot.y + move.y
            val xPoint = robot.x + move.x
            if (field[yPoint][xPoint] != '#' &&
                    field[yPoint][xPoint] != 'W' &&
                    field[yPoint][xPoint] != 'L') { // если след координата робота НЕ стена, НЕ борода, НЕ закрытый лифт
                when (field[yPoint][xPoint]) {
                    '.' -> updateRobot(yPoint, xPoint) // земля
                    '\\' -> { // лямбда
                        updateRobot(yPoint, xPoint)
                        score += 25
                        collectedLambdas++
                    }
                    '!' -> { // бритва
                        updateRobot(yPoint, xPoint)
                        razor++
                    }
                    'O' -> { // открытый лифт
                        field[robot.y][robot.x] = ' '
                        robot.setNewPoint(yPoint, xPoint)
                        score += 50 * collectedLambdas
                        state = State.WON
                        gameover()
                    }
                    '*' -> pushing(move, stones) // двигаем камни
                    '@' -> pushing(move, lambdaStones)
                    else -> updateRobot(yPoint, xPoint)
                }
            } else {}
        }
    }

    // Добавить трамплины

    private fun shave() {
        razor--
        // ??? Придумать как расти, чтобы понять как брить
    }

    private fun waiting() = updateIndicators()

    private fun abort() {
        state = State.ABORTED
        score += 25 * collectedLambdas
        gameover()
    }

    private fun updateIndicators() {
        numberOfSteps++
        score--
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
        if (waterproof < 0) {
            state = State.DEAD
        } // утонул
        if (state == State.DEAD) gameover()
        if (state == State.WON) {
            score += 50 * collectedLambdas
            gameover()
        }
    }

    private fun updateStoneMap(map: MutableMap<Point, Boolean>, oldPoint: Point, newPoint: Point, stoneState: Boolean) {
        map.remove(oldPoint)
        field[oldPoint.y][oldPoint.x] = ' '
        map[newPoint] = stoneState
    }

    private fun updateRobot(newY: Int, newX: Int) {
        field[robot.y][robot.x] = ' '
        field[newY][newX] = 'R'
        robot.setNewPoint(newY, newX)
    }

    private fun falling(fallingStones: Boolean, fallingLambdaStones: Boolean) { // chtobi dvigat kamni nado dumat kak kamen
        if (fallingStones) {
            for (key in stones.keys) { // проходим по всем ключам в камнях
                if (stones.getValue(key)) { // если по какому-то из ключей значение тру - падаем
                    if (field[key.y - 1][key.x] == ' ') { // если ниже пусто
                        updateStoneMap(stones, key, Point(key.y - 1, key.x), true)
                        field[key.y - 1][key.x] = '*'
                    }
                    else if (field[key.y - 1][key.x] == '.' || field[key.y - 1][key.x] == '#') stones[key] = false
                    else if (field[key.y - 1][key.x] == '*' || field[key.y - 1][key.x] == '\\' || field[key.y - 1][key.x] == '@') {
                        if (field[key.y][key.x + 1] == ' ' && field[key.y - 1][key.x + 1] == ' ') {
                            updateStoneMap(stones, key, Point(key.y - 1, key.x + 1), true)
                            field[key.y - 1][key.x + 1] = '*'
                        }
                    }
                    else if (field[key.y - 1][key.x] == 'R') {
                        updateStoneMap(stones, key, Point(key.y - 1, key.x), true)
                        field[key.y - 1][key.x] = '*'
                        state = State.DEAD
                    } // раздавило
                }
            }
        }
        if (fallingLambdaStones) {
            for (key in lambdaStones.keys) {
                if (lambdaStones.getValue(key)) {
                    if (field[key.y - 2][key.x] == '.' ||
                            field[key.y - 2][key.x] == '#' ||
                            field[key.y - 2][key.x] == '*' ||
                            field[key.y - 2][key.x] == '\\' ||
                            field[key.y - 2][key.x] == '@') {
                        lambdaStones.remove(key) // разбился - делит
                        field[key.y][key.x] = ' '
                        field[key.y - 1][key.x] = '\\' // сделать там лямбду
                    }
                    else if (field[key.y - 1][key.x] == 'R') {
                        updateStoneMap(lambdaStones, key, Point(key.y - 1, key.x), true)
                        field[key.y - 1][key.x] = '@'
                        state = State.DEAD
                    }
                    else if (field[key.y - 1][key.x] == ' ') {
                        updateStoneMap(lambdaStones, key, Point(key.y - 1, key.x), true)
                        field[key.y - 1][key.x] = '@'
                    }
                }
            }
        }
        if (!fallingStones && !fallingLambdaStones) isFalling = false
    }

    private fun pushing(move: Move, map: MutableMap<Point, Boolean>) {
        val yPoint = robot.y + move.y
        val xPoint = robot.x + move.x
        val newXPoint = robot.x + 2 * move.x
        if (field[yPoint][newXPoint] == ' ' && (move == Move.RIGHT || move == Move.LEFT)) {
            val oldPoint = Point(yPoint, xPoint)
            val newPoint = Point(yPoint, newXPoint)
            updateStoneMap(map, oldPoint, newPoint, false)
            if (map == stones) field[yPoint][newXPoint] = '*' // появление камня на новой позиции
            else field[yPoint][newXPoint] = '@'
            if (field[yPoint - 1][newXPoint] == ' ' || // это работает неправильно (см. текующую карту), пошла кушать)))))
                    field[yPoint - 1][newXPoint] == '*' ||
                    field[yPoint - 1][newXPoint] == '\\' ||
                    field[yPoint - 1][newXPoint] == '@') {
                isFalling = true
                val nextNewPoint = Point(yPoint - 1, newXPoint)
                updateStoneMap(map, newPoint, nextNewPoint, true)
                if (map == stones) field[yPoint - 1][newXPoint] = '*' // появление камня на новой позиции
                else field[yPoint - 1][newXPoint] = '@'
            }
            updateRobot(yPoint, xPoint)
        } else {
        }
    }

    private fun gameover() {
        stones.clear()
        lambdaStones.clear()
        isFalling = false
        println(score)
    }
}

data class Point(var y: Int, var x: Int) {
    fun setNewPoint(newY: Int, newX: Int) {
        y = newY
        x = newX
    }
}

data class Trampoline(val name: Char, val position: Point) {}

fun main(args: Array<String>) {
    val gbrd = Gameboard(7, 6, BufferedReader(FileReader("maps/map1")))
    gbrd.act("R")
    println(gbrd.field)
}