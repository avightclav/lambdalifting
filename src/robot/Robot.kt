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
    private val listOfChanges = mutableListOf<Change>()
    private var globalPath = ""

    private fun findPathToPoint(point: Point): String {
        val distances = Array(field.size) { Array(field[0].size) { Int.MAX_VALUE } }
        distances[robot.y][robot.x] = 0
        distances[point.y][point.x] = -1
        val points = mutableListOf(robot)
        var path = ""
        var distance = 0
        var y: Int
        var x: Int
        var isPointReached = false
        while (!isPointReached) {
            val temporaryPoints = mutableListOf<Point>()
            for (currentPoint in points) {
                y = currentPoint.y
                x = currentPoint.x
                for (i in (y - 1)..(y + 1))
                    for (j in (x - 1)..(x + 1))
                        if (i == y || j == x) {
                            if (distances[i][j] == Int.MAX_VALUE && (field[i][j] == ' ' || field[i][j] == '.' || field[i][j] == '\\' || field[i][j] == 'O')) {
                                distances[i][j] = distance + 1
                                temporaryPoints.add(Point(i, j))
                            }
                            if (distances[i][j] == -1) {
                                distances[i][j] = distance + 1
                                isPointReached = true
                            }
                        }
            }
            points.clear()
            points.addAll(temporaryPoints)
            distance++
            var fieldHeight = 0
            for (k in 0..(field.size - 1))
                if (field[k].size > fieldHeight) fieldHeight = field[k].size
            if (distance > (field.size * fieldHeight)) return("NOPATH")
        }

        var isPathBuilt = false
        y = point.y
        x = point.x
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
        return path
    }

    private fun updateField() {
        oldField = field
        field = gameboard.getField()
        robot = gameboard.getRobot()
        score = gameboard.getScore()
        listOfLambdas = gameboard.getListOfLambdas()
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

    private fun isLiftBlocked() = findPathToPoint(lift) == "NOPATH"

    private fun getChangedPoints(): MutableMap<Point, Char> {
        val result = mutableMapOf<Point, Char>()
        for (i in 0..(field.size - 1))
            for (j in 0..(field[i].size - 1))
                if (field[i][j] != oldField[i][j]) {
                    result.put(Point(i, j), field[i][j])
                }
        return result
    }

    private fun getNearestLambda(): Point {
        var distance = Integer.MAX_VALUE
        var result = Point(0, 0)
        for (lambda in listOfLambdas) {
            if ((abs(lambda.x - robot.x) + abs(lambda.y - robot.y)) < distance) {
                result = Point(lambda.y, lambda.x)
                distance = abs(lambda.x - robot.x) + abs(lambda.y - robot.y)
            }
        }
        return result
    }

    fun go(): String {
        var i = 0
        while (i < 10000) {
            val currentPath = if (listOfLambdas.isEmpty()) findPathToPoint(lift) else
                findPathToPoint(getNearestLambda())
            for (move in currentPath) {
                if (canMakeMove().contains(move)) {
                    gameboard.act(move.toString())
                    globalPath += move
                    updateField()
                } else break
            }
            if (gameboard.getState() == Gameboard.State.WON) return globalPath
            if (isLiftBlocked() || isRobotBlocked() || gameboard.getState() == Gameboard.State.DEAD) {
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
