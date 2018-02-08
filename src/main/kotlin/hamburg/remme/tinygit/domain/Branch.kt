package hamburg.remme.tinygit.domain

class Branch(val id: String, val name: String, val isRemote: Boolean) {

    val isLocal = !isRemote

    override fun toString() = name

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as Branch

        if (id != other.id) return false
        if (name != other.name) return false
        if (isRemote != other.isRemote) return false

        return true
    }

    override fun hashCode(): Int {
        var result = id.hashCode()
        result = 31 * result + name.hashCode()
        result = 31 * result + isRemote.hashCode()
        return result
    }


}
