package com.lonx.ecjtu.pda.data

import android.os.Parcel
import android.os.Parcelable
import androidx.annotation.Keep

@Keep
data class CourseInfo(
    val section: String,        // e.g., "1-2节"
    val fullName: String,       // e.g., "计算机视觉(上课)"
    val rawTimeInfo: String,    // e.g., "时间：1-16 1,2" (Keeping raw for flexibility)
    val location: String?,      // e.g., "14-205", nullable in case parsing fails or data missing
    val teacher: String?        // e.g., "谭林丰", nullable in case parsing fails or data missing
)

class CourseData {
    data class CourseInfo(
        val courseName: String,
        val courseTime: String = "N/A",
        val courseWeek: String = "N/A",
        val courseLocation: String = "N/A",
        val courseTeacher: String = "N/A"
    ) : Parcelable {
        constructor(parcel: Parcel) : this(
            parcel.readString() ?: "N/A",
            parcel.readString() ?: "N/A",
            parcel.readString() ?: "N/A",
            parcel.readString() ?: "N/A",
            parcel.readString() ?: "N/A"
        )

        override fun writeToParcel(parcel: Parcel, flags: Int) {
            parcel.writeString(courseName)
            parcel.writeString(courseTime)
            parcel.writeString(courseWeek)
            parcel.writeString(courseLocation)
            parcel.writeString(courseTeacher)
        }

        override fun describeContents(): Int {
            return 0
        }

        companion object CREATOR : Parcelable.Creator<CourseInfo> {
            override fun createFromParcel(parcel: Parcel): CourseInfo {
                return CourseInfo(parcel)
            }

            override fun newArray(size: Int): Array<CourseInfo?> {
                return arrayOfNulls(size)
            }
        }
    }

    data class DayCourses(
        val date: String,
        val courses: List<CourseInfo>
    ) : Parcelable {
        constructor(parcel: Parcel) : this(
            parcel.readString() ?: "N/A",
            parcel.createTypedArrayList(CourseInfo.CREATOR) ?: emptyList()
        )

        override fun writeToParcel(parcel: Parcel, flags: Int) {
            parcel.writeString(date)
            parcel.writeTypedList(courses)
        }

        override fun describeContents(): Int {
            return 0
        }

        companion object CREATOR : Parcelable.Creator<DayCourses> {
            override fun createFromParcel(parcel: Parcel): DayCourses {
                return DayCourses(parcel)
            }

            override fun newArray(size: Int): Array<DayCourses?> {
                return arrayOfNulls(size)
            }
        }
    }

    companion object {
        fun DayCourses(): DayCourses {
            return DayCourses("课表为空", listOf(
                CourseInfo("N/A", "N/A", "N/A", "N/A", "N/A")
            ))

        }
    }
}