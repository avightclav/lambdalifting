package robot

import gameboard.Gameboard
import gameboard.Point
import java.lang.Math.abs

class Robot(inputField: String) {

    private var gameboard = Gameboard(inputField)
    private var field = gameboard.getField()
    private var oldField = gameboard.getField()
    private var robot = gameboard.getRobot()
    private var score = gameboard.getScore()
    private val lift = gameboard.getLift()
    private var listOfLambdas = gameboard.getListOfLambdas()
    private var listOfUnreachableLambdas = mutableListOf<Point>()
    private val listOfChanges = mutableListOf<Change>()
    private var globalPath = ""
    private var fieldHeight = 0

    init {
        for (k in 0..(field.size - 1))
            if (field[k].size > fieldHeight) fieldHeight = field[k].size
    }

    private fun findPathToPoint(points: List<Point>): Pair<Point, String> {
        val distances = Array(field.size) { Array(fieldHeight) { Int.MAX_VALUE } }
        distances[robot.y][robot.x] = 0
        for (point in points) {
            distances[point.y][point.x] = -1
        }
        val waveFront = mutableListOf(robot)
        var path = ""
        var distance = 0
        var y: Int
        var x: Int
        var isPointReached = false
        var nearestPoint = Point(-1, -1)
        while (!isPointReached) {
            val temporaryPoints = mutableListOf<Point>()
            for (currentPoint in waveFront) {
                y = currentPoint.y
                x = currentPoint.x
                for (i in (y - 1)..(y + 1))
                    for (j in (x - 1)..(x + 1))
                        if (i == y || j == x) {
                            if (distances[i][j] == Int.MAX_VALUE && (field[i][j] == ' ' || field[i][j] == '.' || field[i][j] == '\\' || field[i][j] == 'O' || field[i][j] == '!')) {
                                distances[i][j] = distance + 1
                                temporaryPoints.add(Point(i, j))
                            }
                            if (distances[i][j] == -1) {
                                distances[i][j] = distance + 1
                                isPointReached = true
                                nearestPoint = Point(i, j)
                            }
                        }
            }
            waveFront.clear()
            waveFront.addAll(temporaryPoints)
            distance++
            if (distance > (field.size * fieldHeight)) return Pair(points[0], "NOPATH")
        }

        var isPathBuilt = false
        y = nearestPoint.y
        x = nearestPoint.x
        while (!isPathBuilt) {
            when {
                y > 0 && distances[y - 1][x] == distance - 1 -> {
                    y--
                    path = "U$path"
                }
                y < field.size && distances[y + 1][x] == distance - 1 -> {
                    y++
                    path = "D$path"
                }
                x > 0 && distances[y][x - 1] == distance - 1 -> {
                    x--
                    path = "R$path"
                }
                x < distances[y].size && distances[y][x + 1] == distance - 1 -> {
                    x++
                    path = "L$path"
                }
            }
            distance--
            if (distance == 0) isPathBuilt = true
        }
        return Pair(nearestPoint, path)
    }

    private fun updateField() {
        for (i in 0..(field.size - 1))
            for (j in 0..(field[i].size - 1))
                oldField[i][j] = field[i][j]
        field = gameboard.getField()
        robot = gameboard.getRobot()
        score = gameboard.getScore()
        listOfLambdas = gameboard.getListOfLambdas()
        listOfLambdas.removeAll(listOfUnreachableLambdas)
        listOfChanges.add(Change(globalPath, score, getChangedPoints()))
    }

    private fun goBackTo(step: Int) {
        val temp = listOfChanges.subList(step + 1, listOfChanges.size)
        listOfChanges.removeAll(temp)
        globalPath = listOfChanges[step].path
    }

    private fun canMakeMove(): String {
        var result = ""
        if (field[robot.y][robot.x + 1] == ' ' || field[robot.y][robot.x + 1] == '.' || field[robot.y][robot.x + 1] == 'O'
                || field[robot.y][robot.x + 1] == '\\' || field[robot.y][robot.x + 1] == '*' && field[robot.y][robot.x + 2] == ' ')
            result += 'R'
        if (field[robot.y][robot.x - 1] == ' ' || field[robot.y][robot.x - 1] == '.' || field[robot.y][robot.x - 1] == 'O'
                || field[robot.y][robot.x - 1] == '\\' || field[robot.y][robot.x - 1] == '*' && field[robot.y][robot.x - 2] == ' ')
            result += 'L'
        if (field[robot.y + 1][robot.x] == ' ' || field[robot.y + 1][robot.x] == '.' || field[robot.y + 1][robot.x] == 'O'
                || field[robot.y + 1][robot.x] == '\\')
            result += 'U'
        if (field[robot.y - 1][robot.x] == ' ' || field[robot.y - 1][robot.x] == '.' || field[robot.y - 1][robot.x] == 'O'
                || field[robot.y - 1][robot.x] == '\\')
            result += 'D'
        return result
    }

    private fun isRobotBlocked() = canMakeMove() == ""

    private fun isLiftBlocked() = findPathToPoint(listOf(lift)).second == "NOPATH"

    private fun getChangedPoints(): MutableMap<Point, Char> {
        val result = mutableMapOf<Point, Char>()
        for (i in 0..(field.size - 1))
            for (j in 0..(field[i].size - 1))
                if (field[i][j] != oldField[i][j]) {
                    result.put(Point(i, j), field[i][j])
                }
        return result
    }

//    private fun getNearestLambda(): Point {
//        var distance = Integer.MAX_VALUE
//        var result = Point(0, 0)
//        val initialPoint = if (gameboard.getFlooding() > 0) Point(0, 0) else Point(robot.y, robot.x)
//        for (lambda in listOfLambdas) {
//            if (!listOfUnreachableLambdas.contains(lambda) && (abs(lambda.x - initialPoint.x) + abs(lambda.y - initialPoint.y)) < distance) {
//                result = Point(lambda.y, lambda.x)
//                distance = abs(lambda.x - initialPoint.x) + abs(lambda.y - initialPoint.y)
//            }
//        }
//        return result
//    }

    private fun isUnderStone() = field[robot.y + 1][robot.x] == '*'

    private fun isRightUnderStone() = (field[robot.y + 1][robot.x - 1] == '*' && (field[robot.y][robot.x - 1] == '*' || field[robot.y][robot.x - 1] == '\\'))

    private fun isLeftUnderStone() = (field[robot.y + 1][robot.x + 1] == '*' && field[robot.y][robot.x + 1] == '*')

    private fun isLambdaUnreachable(lambda: Point): Boolean {
        val x = lambda.x
        val y = lambda.y
        return (field[y + 1][x] == '*' && !(field[y][x + 1] == ' ' || field[y][x + 1] == '.' || field[y][x + 1] == '\\' || field[y][x + 1] == 'O' || field[y][x + 1] == '!' || field[y][x + 1] == 'R')
                && !(field[y][x - 1] == ' ' || field[y][x - 1] == '.' || field[y][x - 1] == '\\' || field[y][x - 1] == 'O' || field[y][x - 1] == '!' || field[y][x - 1] == 'R'))
    }

    fun go(): String {
        var i = 0
        while (i < 10000) {

            val result = if (listOfLambdas.isEmpty() && listOfUnreachableLambdas.isEmpty()) findPathToPoint(listOf(lift)) else findPathToPoint(listOfLambdas)

            val currentPath = result.second
            val nearestPoint = result.first

            if (currentPath == "NOPATH" || isLambdaUnreachable(nearestPoint) && nearestPoint != lift) {
                listOfUnreachableLambdas.add(nearestPoint)
                listOfLambdas.remove(nearestPoint)
                if (!listOfLambdas.isEmpty()) continue
            }

            val numberOfLambdas = listOfLambdas.size
            var numberOfLambdas1: Int
            // println(currentPath)
            for (move in currentPath) {
                if (canMakeMove().contains(move)) {
                    if (isUnderStone() && move == 'D') {
                        if (canMakeMove().contains('R')) {
                            gameboard.act("R")
                            globalPath += 'R'
                            updateField()
                            break
                        } else if (canMakeMove().contains('L')) {
                            if (canMakeMove().contains('L')) gameboard.act("L")
                            globalPath += 'L'
                            updateField()
                            break
                        } else {
                            globalPath += 'A'
                            return globalPath
                        }
                    } else if (isLeftUnderStone() && move == 'D') {
                        if (canMakeMove().contains('U')) {
                            gameboard.act("U")
                            globalPath += 'U'
                            updateField()
                            if (canMakeMove().contains('L')) {
                                gameboard.act("L")
                                globalPath += 'L'
                                updateField()
                            } else if (canMakeMove().contains('U')) {
                                gameboard.act("U")
                                globalPath += 'U'
                                updateField()
                            }
                            break
                        }
                    } else if (isRightUnderStone() && move == 'D') {
                        if (canMakeMove().contains('U')) {
                            gameboard.act("U")
                            globalPath += 'U'
                            updateField()
                            if (canMakeMove().contains('R')) {
                                gameboard.act("R")
                                globalPath += 'R'
                                updateField()
                            } else if (canMakeMove().contains('U')) {
                                gameboard.act("U")
                                globalPath += 'U'
                                updateField()
                            }
                            break
                        }
                    } else {
                        gameboard.act(move.toString())
                        globalPath += move
                    }
                    updateField()
                    //val newNearestLambda = getNearestLambda()
                    //if (newNearestLambda != nearestPoint) break
                } else break
                numberOfLambdas1 = listOfLambdas.size
                if (numberOfLambdas1 != numberOfLambdas) break
            }
            if (gameboard.getState() == Gameboard.State.WON) return globalPath
            if (listOfLambdas.isEmpty() && (isLiftBlocked() || listOfUnreachableLambdas.isNotEmpty())|| isRobotBlocked() || gameboard.getState() == Gameboard.State.DEAD) {
                var step = 0
                var maxScore = 0
                for (k in 0 until listOfChanges.size) {
                    if (listOfChanges[k].score > maxScore) {
                        maxScore = listOfChanges[k].score
                        step = k
                    }
                }
                goBackTo(step)
                break
            }
            i++
        }
        if (gameboard.getState() != Gameboard.State.WON) globalPath += 'A'
        return globalPath
    }
}

data class Change(val path: String, val score: Int, val changedPoints: Map<Point, Char>)
