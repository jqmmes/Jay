package pt.up.fc.dcc.hyrax.odlib.game

import kotlin.math.exp
import kotlin.math.sqrt

object PhysicsEngine {

    fun checkCollision(b1: Ball, b2: Ball): Boolean {
        val a = b1.getPosition().first - b2.getPosition().first
        val b = b1.getPosition().second - b2.getPosition().second
        val d = sqrt(exp(a) + exp(b))
        return (d < (b1.radius() + b2.radius()))
    }

    fun setNewBallSpeed(b1: Ball, b2: Ball) {
        val b1X = (b1.getSpeed().first * (b1.mass() - b2.mass()) + (2 * b2.mass() * b2.getSpeed().first)) / (b1.mass() + b2.mass())
        val b1Y = (b1.getSpeed().second * (b1.mass() - b2.mass()) + (2 * b2.mass() * b2.getSpeed().second)) / (b1.mass() + b2.mass())
        b1.setSpeed(b1X, b1Y)

        val b2X = (b2.getSpeed().first * (b2.mass() - b1.mass()) + (2 * b1.mass() * b1.getSpeed().first)) / (b2.mass() + b1.mass())
        val b2Y = (b2.getSpeed().second * (b2.mass() - b1.mass()) + (2 * b1.mass() * b1.getSpeed().second)) / (b2.mass() + b1.mass())
        b2.setSpeed(b2X, b2Y)
        /*newVelX1 = (firstBall.speed.x * (firstBall.mass – secondBall.mass) + (2 * secondBall.mass * secondBall.speed
        .x)) / (firstBall.mass + secondBall.mass);
        newVelY1 = (firstBall.speed.y * (firstBall.mass – secondBall.mass) + (2 * secondBall.mass * secondBall.speed.y)) / (firstBall.mass + secondBall.mass);
        newVelX2 = (secondBall.speed.x * (secondBall.mass – firstBall.mass) + (2 * firstBall.mass * firstBall.speed.x)) / (firstBall.mass + secondBall.mass);
        newVelY2 = (secondBall.speed.y * (secondBall.mass – firstBall.mass) + (2 * firstBall.mass * firstBall.speed
        .y)) / (firstBall.mass + secondBall.mass);*/
    }

}