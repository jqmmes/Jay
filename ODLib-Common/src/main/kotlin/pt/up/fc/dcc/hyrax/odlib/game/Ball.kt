package pt.up.fc.dcc.hyrax.odlib.game

@Suppress("unused")
class Ball {
    constructor(x: Float, y: Float) {
        this.x = x
        this.y = y
    }

    constructor(x: Float, y: Float, speed: Speed) {
        Ball(x, y)
        this.speed = speed
    }


    private var speed: Speed = Speed(0.0F, 0.0F)
    private var x: Float = 0.0F
    private var y: Float = 0.0F
    private var r: Int = 1
    private var m: Int = 1

    fun getPosition(): Pair<Float, Float> {
        return Pair(x, y)
    }

    fun setPosition(x: Float, y: Float) {
        this.x = x
        this.y = y
    }

    fun getSpeed(): Pair<Float, Float> {
        return speed.getSpeed()
    }

    fun setSpeed(x: Float, y: Float) {
        speed.setSpeed(x, y)
    }

    fun radius(): Int {
        return r
    }

    fun mass(): Int {
        return m
    }

    companion object {
        class Speed(private var x: Float, private var y: Float) {
            internal fun getSpeed(): Pair<Float, Float> {
                return Pair(x, y)
            }

            internal fun setSpeed(x: Float, y: Float) {
                this.x = x
                this.y = y
            }
        }
    }
}