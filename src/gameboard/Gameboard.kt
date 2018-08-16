package gameboard

import java.io.File

class Gameboard(inputField: String) {

    private val field = mutableListOf<MutableList<Char>>()
    private var stones = ArrayList<Point>()
    private var lambdaStones = ArrayList<Point>()
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
    private var currentWaterproof = 10
    private var numberOfSteps = 0
    private var collectedLambdas = 0
    private var allLambdas = 0
    private var lift = Point(-1, -1)

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
                        '*' -> stones.add(Point(j, k))
                        '@' -> {
                            lambdaStones.add(Point(j, k))
                            allLambdas++
                        }
                        'W' -> beards.add(Point(j, k))
                        '\\' -> allLambdas++
                        'L' -> lift.setNewPoint(j, k)
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
                                    currentWaterproof = waterproof
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
        if (currentWaterproof >= 0) {
            val yPoint = robot.y + move.y
            val xPoint = robot.x + move.x
            if (field[yPoint][xPoint] != '#' &&
                    field[yPoint][xPoint] != 'W' &&
                    field[yPoint][xPoint] != 'L' &&
                    (field[yPoint][xPoint] != '1' ||
                    field[yPoint][xPoint] != '2' ||
                    field[yPoint][xPoint] != '3' ||
                    field[yPoint][xPoint] != '4' ||
                    field[yPoint][xPoint] != '5' ||
                    field[yPoint][xPoint] != '6' ||
                    field[yPoint][xPoint] != '7' ||
                    field[yPoint][xPoint] != '8' ||
                    field[yPoint][xPoint] != '9')) { // если след координата робота НЕ стена, НЕ борода, НЕ закрытый лифт, НЕ выход трамплина
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
                        state = State.WON
                    }
                    '*' -> pushing(move, stones, stones.indexOf(Point(yPoint, xPoint))) // двигаем камни
                    '@' -> pushing(move, lambdaStones, lambdaStones.indexOf(Point(yPoint, xPoint)))
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
            }
            else {}
        }
        updateIndicators()
    }

    private fun growBeard() {
        val temporaryBeards = mutableListOf<Point>()
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
        falling()
        if (currentGrowth == growth) {
            growBeard()
            currentGrowth = 0
        }
        if (waterLevel >= robot.y + 1) { // если робот в воде, уменьшаем вотерпруф
            currentWaterproof--
        }
        if (waterLevel < robot.y + 1) { // если вынырнул - обновляем
            currentWaterproof = waterproof
        }
        if (currentWaterproof < 0) state = State.DEAD
        if (flooding > 0 && numberOfSteps == flooding) { // если установлена скорость прибывания воды и
            // количество шагов равно ей, тогда поднять воду на 1 лвл и обнулить кол-во шагов
            waterLevel++
            numberOfSteps = 0
        }
        if (collectedLambdas == allLambdas) field[lift.y][lift.x] = 'O'
        if (state == State.DEAD) gameover()
        if (state == State.WON) {
            score += 50 * collectedLambdas
            gameover()
        }
    }

    private fun updateRobot(newY: Int, newX: Int) {
        field[newY][newX] = 'R'
        field[robot.y][robot.x] = ' '
        robot.setNewPoint(newY, newX)
    }

    private fun falling() { // chtobi dvigat kamni nado dumat kak kamen
        for (i in 0 until stones.size) {
            val currentStone = stones.get(i)
            val cellUnderStone = field[currentStone.y - 1][currentStone.x]
            when (cellUnderStone) {
                ' ' -> {
                    field[currentStone.y][currentStone.x] = ' '
                    stones.set(i, Point(currentStone.y - 1, currentStone.x))
                    if (Point(currentStone.y - 2, currentStone.x) == robot) state = State.DEAD
                }
                '\\' -> {
                    if (field[currentStone.y][currentStone.x + 1] == ' ' && field[currentStone.y - 1][currentStone.x + 1] == ' ') {
                        field[currentStone.y][currentStone.x] = ' '
                        stones.set(i, Point(currentStone.y - 1, currentStone.x + 1))
                        if (Point(currentStone.y - 2, currentStone.x + 1) == robot) state = State.DEAD
                    }
                }
                '*', '@' -> {
                    if (field[currentStone.y][currentStone.x + 1] == ' ' && field[currentStone.y - 1][currentStone.x + 1] == ' ') {
                        field[currentStone.y][currentStone.x] = ' '
                        stones.set(i, Point(currentStone.y - 1, currentStone.x + 1))
                        if (Point(currentStone.y - 2, currentStone.x + 1) == robot) state = State.DEAD
                    } else if (field[currentStone.y][currentStone.x - 1] == ' ' && field[currentStone.y - 1][currentStone.x - 1] == ' ') {
                        field[currentStone.y][currentStone.x] = ' '
                        stones.set(i, Point(currentStone.y - 1, currentStone.x - 1))
                        if (Point(currentStone.y - 2, currentStone.x - 1) == robot) state = State.DEAD
                    }

                }
            }
        }
        val lambdas = arrayListOf<Point>()
        for (i in 0 until lambdaStones.size) {
            val currentStone = lambdaStones.get(i)
            val cellUnderStone = field[currentStone.y - 1][currentStone.x]
            when (cellUnderStone) {
                ' ' -> {
                    if (field[currentStone.y - 2][currentStone.x] != ' ') { // разбиение
                        field[currentStone.y][currentStone.x] = ' '
                        lambdas.add(Point(currentStone.y - 1, currentStone.x))
                        lambdaStones.set(i, Point(field.size - 1, 0)) //поставить в верхний левый угол
                        if (Point(currentStone.y - 2, currentStone.x) == robot) state = State.DEAD
                    } else { // падение
                        field[currentStone.y][currentStone.x] = ' '
                        lambdaStones.set(i, Point(currentStone.y - 1, currentStone.x))
                    }
                }
                '\\' -> {
                    if (field[currentStone.y][currentStone.x + 1] == ' ' && field[currentStone.y - 1][currentStone.x + 1] == ' ') {
                        field[currentStone.y][currentStone.x] = ' '
                        if (field[currentStone.y - 2][currentStone.x + 1] != ' ') { // разбиение
                            lambdas.add(Point(currentStone.y - 1, currentStone.x + 1))
                            lambdaStones.set(i, Point(field.size - 1, 0)) //поставить в верхний левый угол
                            if (Point(currentStone.y - 2, currentStone.x + 1) == robot) state = State.DEAD
                        } else lambdaStones.set(i, Point(currentStone.y - 1, currentStone.x + 1))
                    }
                }
                '*', '@' -> {
                    if (field[currentStone.y][currentStone.x + 1] == ' ' && field[currentStone.y - 1][currentStone.x + 1] == ' ') {
                        field[currentStone.y][currentStone.x] = ' '
                        if (field[currentStone.y - 2][currentStone.x + 1] != ' ') { // разбиение
                            lambdas.add(Point(currentStone.y - 1, currentStone.x + 1))
                            lambdaStones.set(i, Point(field.size - 1, 0)) //поставить в верхний левый угол
                            if (Point(currentStone.y - 2, currentStone.x + 1) == robot) state = State.DEAD
                        } else lambdaStones.set(i, Point(currentStone.y - 1, currentStone.x + 1))
                    } else if (field[currentStone.y][currentStone.x - 1] == ' ' && field[currentStone.y - 1][currentStone.x - 1] == ' ') {
                        field[currentStone.y][currentStone.x] = ' '
                        if (field[currentStone.y - 2][currentStone.x - 1] != ' ') { // разбиение
                            lambdas.add(Point(currentStone.y - 1, currentStone.x - 1))
                            lambdaStones.set(i, Point(field.size - 1, 0)) //поставить в верхний левый угол
                            if (Point(currentStone.y - 2, currentStone.x - 1) == robot) state = State.DEAD
                        } else lambdaStones.set(i, Point(currentStone.y - 1, currentStone.x - 1))
                    }
                }
            }
        }
        for (lambda in lambdas) {
            field[lambda.y][lambda.x] = '\\'
        }
        lambdaStones = ArrayList(lambdaStones.toSet()) // удаление одинаковых камней
        for (lambdaStone in lambdaStones) {
            field[lambdaStone.y][lambdaStone.x] = '@'
        }
        stones = ArrayList(stones.toSet()) // удаление одинаковых камней
        for (stone in stones) {
            field[stone.y][stone.x] = '*'
        }
    }

    private fun pushing(move: Move, array: ArrayList<Point>, i: Int) {
        val yPoint = robot.y + move.y
        val xPoint = robot.x + move.x
        val newXPoint = robot.x + 2 * move.x
        if (field[yPoint][newXPoint] == ' ' && (move == Move.RIGHT || move == Move.LEFT)) {
            val oldPoint = Point(yPoint, xPoint)
            val newPoint = Point(yPoint, newXPoint)
            field[oldPoint.y][oldPoint.x] = ' '
            array.set(i, newPoint)
            if (array == stones) field[yPoint][newXPoint] = '*' // появление камня на новой позиции
            else field[yPoint][newXPoint] = '@'
            updateRobot(yPoint, xPoint)
        } else {
        }
    }

    private fun gameover() {
        stones.clear()
        lambdaStones.clear()
        println(score)
    }

    fun getScore(): Int {
        return score
    }

    fun getState(): State {
        return state
    }

    fun printField() {
        for (i in (field.size - 1) downTo 0) {
            for (j in 0..(field[i].size - 1))
                print(field[i][j])
            println()
        }
        println("Score: $score")
        println("State: $state")
    }
}

data class Point(var y: Int, var x: Int) {
    fun setNewPoint(newY: Int, newX: Int) {
        y = newY
        x = newX
    }
}
