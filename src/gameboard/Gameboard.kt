package gameboard

import java.io.File

class Gameboard(inputField: String) {

    private val field = mutableListOf<MutableList<Char>>()
    private var stones = mutableMapOf<Point, Boolean>()
    private var lambdaStones = mutableMapOf<Point, Boolean>()
    private var trampolinesPoints = mutableMapOf<Char, Point>() // координаты трамплинов
    private var trampolines = mutableMapOf<Char, Char>() // какой трамплин куда ведёт
    private var robot = Point(-1, -1)
    private val beards = mutableListOf<Point>()
    private var score = 0
    private var growth = 25 // количество шагов через которое вырастет борода
    private var currentGrowth = 0
    private var razors = 0
    private var flooding = 0    // скорость прибывания воды (количество шагов через которое вода поднимается на 1 уровень)
    private var waterLevel = 0   // уровень воды
    private var waterproof = 10 // сколько шагов без выныривания можно пройти
    private var numberOfSteps = 0
    private var collectedLambdas = 0
    private var isFalling = false // падают ща камни или не

    init {
        val lineList = mutableListOf<String>()
        File(inputField).useLines { lines -> lines.forEach { lineList.add(it) } }
        var j = -1
        for (line in lineList) {
            if (line != "") j++
            else break
        }
        for (i in 0..j) field.add(mutableListOf())
        for (line in lineList) {
            if (j >= 0) {
                for ((k, char) in line.toCharArray().withIndex()) {
                    field[j].add(k, char)
                    when (char) {
                        'R' -> robot.setNewPoint(j, k)
                        '*' -> stones[Point(j, k)] = false
                        '@' -> lambdaStones[Point(j, k)] = false
                        'W' -> beards.add(Point(j, k))
                        in 'A'..'I' -> trampolinesPoints[char] = Point(j, k)
                        in '1'..'9' -> trampolinesPoints[char] = Point(j, k)
                    }
                }
                j--
            } else { // наверно, это можно сделать покрасивее
                var matchResult = Regex("""Growth (\d+)""").find(line)
                if (matchResult != null) {
                    growth = matchResult.groupValues[1].toInt()
                } else {
                    matchResult = Regex("""Razors (\d+)""").find(line)
                    if (matchResult != null) {
                        razors = matchResult.groupValues[1].toInt()
                    } else {
                        matchResult = Regex("""Water (\d+)""").find(line)
                        if (matchResult != null) {
                            waterLevel = matchResult.groupValues[1].toInt()
                        } else {
                            matchResult = Regex("""Flooding (\d+)""").find(line)
                            if (matchResult != null) {
                                flooding = matchResult.groupValues[1].toInt()
                            } else {
                                matchResult = Regex("""Waterproof (\d+)""").find(line)
                                if (matchResult != null) {
                                    waterproof = matchResult.groupValues[1].toInt()
                                } else {
                                    matchResult = Regex("""Trampoline ([A-I]) targets (\d)""").find(line)
                                    if (matchResult != null) {
                                        trampolines[matchResult.groupValues[1][0]] = matchResult.groupValues[2][0]
                                    }
                                }

                            }
                        }
                    }
                }
            }
        }
    }

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
                    field[yPoint][xPoint] != 'L' &&
                    field[yPoint][xPoint] !in '1'..'9') { // если след координата робота НЕ стена, НЕ борода, НЕ закрытый лифт, НЕ выход трамплина
                when (field[yPoint][xPoint]) {
                    '.' -> updateRobot(yPoint, xPoint) // земля
                    '\\' -> { // лямбда
                        updateRobot(yPoint, xPoint)
                        score += 25
                        collectedLambdas++
                    }
                    '!' -> { // бритва
                        updateRobot(yPoint, xPoint)
                        razors++
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
                    in 'A'..'I' -> { // трамплин
                        val char = field[yPoint][xPoint]
                        val robotY = trampolinesPoints[trampolines[char]]!!.y
                        val robotX = trampolinesPoints[trampolines[char]]!!.x
                        val trampolinesToRemove = mutableListOf<Char>()
                        field[trampolinesPoints[trampolines[char]]!!.y][trampolinesPoints[trampolines[char]]!!.x] = ' '
                        for (entry in trampolines.entries) {
                            if (entry.value == trampolines[char]) {
                                trampolinesToRemove.add(entry.key)
                                field[trampolinesPoints[entry.key]!!.y][trampolinesPoints[entry.key]!!.x] = ' '
                            }
                        }
                        for (trampolineToRemove in trampolinesToRemove) {
                            trampolines.remove(trampolineToRemove)
                        }
                        updateRobot(robotY, robotX)
                    }
                    else -> updateRobot(yPoint, xPoint)
                }
            } else {}
        }
    }

    // Добавить трамплины

    private fun growBeard() {
        val temporaryBeards: MutableList<Point> = mutableListOf()
        for (beard in beards) {
            for (y in (beard.y - 1)..(beard.y + 1))
                for (x in (beard.x - 1)..(beard.x + 1))
                    if (field[y][x] == ' ') {
                        temporaryBeards.add(Point(y, x))
                        field[y][x] = 'W'
                    }
        }
        beards.addAll(temporaryBeards)
    }

    private fun shave() {
        updateIndicators()
        if (razors > 0) {
            razors--
            for (y in (robot.y - 1)..(robot.y + 1))
                for (x in (robot.x - 1)..(robot.x + 1)) {
                    if (field[y][x] == 'W') {
                        beards.remove(Point(y, x))
                        field[y][x] = ' '
                    }
                }
        }
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
        currentGrowth++
        if (currentGrowth == growth) {
            growBeard()
            currentGrowth = 0
        }
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

    fun printField() {
        for (i in (field.size - 1) downTo 0) {
            for (j in 0..(field[i].size - 1))
                print(field[i][j])
            println()
        }
        println("Score: $score")
    }
}

data class Point(var y: Int, var x: Int) {
    fun setNewPoint(newY: Int, newX: Int) {
        y = newY
        x = newX
    }
}

fun main(args: Array<String>) {
    //val gameboard = Gameboard("maps/beard1.map")
    //gameboard.act("WWWWWWWWWWWWWWW")
    //gameboard.printField()

    val gameboard = Gameboard("maps/trampoline1.map")
    gameboard.act("DLLU")
    gameboard.printField()
}