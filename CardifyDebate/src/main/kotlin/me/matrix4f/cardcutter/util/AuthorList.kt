package me.matrix4f.cardcutter.util

import me.matrix4f.cardcutter.core.card.Author

class AuthorList(val value: Array<Author>) {

    constructor(single: Author): this(arrayOf(single))

}