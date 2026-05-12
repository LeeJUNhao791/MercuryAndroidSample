package com.ffalcon.mercury.android.sdk.demo.ui.entity

/**
 * 联系人数据类
 * 存储联系人的基本信息，包括显示名称、电话号码和ID
 * @param displayName 显示名称
 * @param phoneNum 电话号码
 * @param id 联系人唯一标识
 */
data class Contact(
    val displayName: String,
    val phoneNum: String,
    val id: Long,

    ) {
    companion object {
        // 无效联系人对象，用作占位符或默认值
        val Invalid = Contact(
            id = -1,
            displayName = "",
            phoneNum = ""
        )
    }
}

/**
 * 生成联系人列表
 * 创建指定数量的测试联系人数据，可选择添加占位符
 * @param placeHolder 是否添加无效联系人作为占位符
 * @return 生成的联系人列表
 */
fun contactList(placeHolder:Boolean = false): ArrayList<Contact> {
    val retVal = arrayListOf<Contact>()
    // 生成101个测试联系人
    for (i in 0..100L) {
        retVal.add(Contact("James - $i", "XXXXXXXX", i))
    }
    // 如果需要占位符，则添加两个无效联系人
    if (placeHolder) {
        for (i in 0..1) {
            retVal.add(Contact.Invalid)
        }
    }

    return retVal
}