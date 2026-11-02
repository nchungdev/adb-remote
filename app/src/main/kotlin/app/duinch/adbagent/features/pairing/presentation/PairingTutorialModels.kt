package app.duinch.adbagent.features.pairing.presentation

internal data class TutorialSlide(
    val stepLabel: String,
    val title: String,
    val description: String,
    val illustrationType: Int
)

internal val SLIDES = listOf(
    TutorialSlide(
        stepLabel = "HƯỚNG DẪN ĐỘNG",
        title = "3 Bước thực hiện nhanh",
        description = "Bước 1: Bật Gỡ lỗi không dây\nBước 2: Chọn Ghép nối bằng mã ghép nối\nBước 3: Chụp ảnh màn hình để AI tự ghép đôi",
        illustrationType = 0
    ),
    TutorialSlide(
        stepLabel = "BƯỚC 1 / 3",
        title = "Bật Gỡ lỗi không dây",
        description = "Bấm nút \"Mở Cài đặt gỡ lỗi\" bên dưới, tìm và gạt bật công tắc \"Gỡ lỗi không dây\".",
        illustrationType = 1
    ),
    TutorialSlide(
        stepLabel = "BƯỚC 2 / 3",
        title = "Chọn Ghép nối bằng mã",
        description = "Bấm thẳng vào dòng \"Ghép nối bằng mã ghép nối\" để hiện popup chứa mã 6 chữ số.",
        illustrationType = 2
    ),
    TutorialSlide(
        stepLabel = "BƯỚC 3 / 3",
        title = "Chụp ảnh màn hình (Screenshot)",
        description = "Nhấn đồng thời Phím Nguồn + Giảm âm lượng. AI sẽ tự động đọc mã và ghép đôi tức thì!",
        illustrationType = 3
    )
)
