# 오류 화면(CrashActivity)에 보이는 호출 줄 번호는 유지한다 (클래스·메서드 이름은 난독화되며,
# 원래 이름은 빌드 때 생기는 mapping.txt로 되돌려 볼 수 있다).
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# Room 엔티티/DAO/Converter와 enum은 Room 생성 코드가 이름·값으로 접근한다.
-keepclassmembers enum * { public static **[] values(); public static ** valueOf(java.lang.String); }
