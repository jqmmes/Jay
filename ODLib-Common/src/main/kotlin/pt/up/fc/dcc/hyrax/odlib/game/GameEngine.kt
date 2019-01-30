package pt.up.fc.dcc.hyrax.odlib.game

import java.util.*

object GameEngine {
    private var nBalls: Int = 0
    private val ballList: LinkedList<Ball> = LinkedList()
    private var x = 100
    private var y = 100

    fun startEngine(nBalls: Int) {
        this.nBalls = nBalls
        for (x in 0..nBalls) {
            var next = false
            while (!next) {
                val ball = Ball(Random().nextInt(GameEngine.x).toFloat(), Random().nextInt(GameEngine.y).toFloat())
                if (!isBallColliding(ball)) {
                    ball.setSpeed((Random().nextInt(2) - 1).toFloat(), (Random().nextInt(2) - 1).toFloat())
                    ballList.addLast(ball)
                    next = true
                }
            }
        }
    }

    fun updatePositions() {

    }


    private fun isBallColliding(b: Ball, ballList: LinkedList<Ball> = this.ballList): Boolean {
        for (ball in ballList) {
            if (PhysicsEngine.checkCollision(b, ball)) return true
        }
        return false
    }

}