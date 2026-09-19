package com.github.ilife798.pay

// iOS 端不支持应用内支付；Change 2 评估替代方案。
actual suspend fun payWithAlipay(orderInfo: String): AlipayPayResult = AlipayPayResult(success = false, message = "iOS 端暂不支持应用内支付")
