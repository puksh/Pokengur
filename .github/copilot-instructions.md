# Pokengur - Instrukcje dla GitHub Copilot

## 1. Przegląd projektu

**Pokengur** to fork aplikacji **OpenImgur** - klienta Imgur dla systemu Android, pierwotnie stworzonego przez Kennyc1012. Projekt jest aktywnie rozwijany (ostatnie zmiany: luty 2026).

### Podstawowe informacje techniczne:

- **Nazwa aplikacji**: Pokengur
- **Wersja**: 5.3.0 (kod wersji: 92)
- **Pakiet główny**: `com.puksh.pokenimgur`
- **App ID**: `com.puksh.poken.imgur`
- **Język programowania**: Java
- **Minimalne SDK**: 17 (Android 4.2 Jelly Bean)
- **Celowe SDK**: 28 (Android 9 Pie)
- **Kompilacja SDK**: 28
- **Wersja Java**: 1.7 (source i target compatibility)
- **Gradle**: 3.2.1 (Android plugin)
- **Android Build Tools**: 28.0.3

## 2. Konfiguracja środowiska deweloperskiego

### Wymagania systemowe:

- **JDK**: Wersja 8 (przykład: `C:\Program Files\Java\jdk1.8.0_481`)
- **Zmienne środowiskowe**:
  - `JAVA_HOME` - musi wskazywać na JDK 8
  - `API_CLIENT_ID` - klucz API Imgur (wymagany do budowania)
  - `API_CLIENT_SECRET` - sekret API Imgur (wymagany do budowania)

### Repozytoria:

- Google Maven (`https://maven.google.com`)
- Maven Central
- JitPack (`https://jitpack.io`)

## 3. Budowanie aplikacji

### Komendy budowania (Windows):

```powershell
# Debug build
$env:JAVA_HOME="C:\Program Files\Java\jdk1.8.0_481"; .\gradlew.bat --no-daemon assembleDebug

# Release build (podpisany debug keystore)
$env:JAVA_HOME="C:\Program Files\Java\jdk1.8.0_481"; .\gradlew.bat --no-daemon assembleRelease
```

### Pliki wyjściowe:

- **Debug**: `app/build/outputs/apk/debug/Pokengur-5.3.0-debug.apk`
- **Release**: `app/build/outputs/apk/release/Pokengur-5.3.0-release.apk`

### Podpisywanie:

- Oba typy buildów są podpisywane debug keystore (`~/.android/debug.keystore`)
- Parametry debug keystore:
  - Store password: `android`
  - Key alias: `androiddebugkey`
  - Key password: `android`

### Flavour:

- Brak product flavors - pojedynczy wariant aplikacji

## 4. Architektura i biblioteki

### Warstwa sieciowa:

- **Retrofit 2.1.0** - klient HTTP typu REST
- **OkHttp 3.4.2** - klient HTTP
- **Gson 2.8.0** - serializacja/deserializacja JSON
- **OAuthInterceptor** - własna implementacja obsługi OAuth

### Ładowanie i wyświetlanie mediów:

- **Universal Image Loader 1.9.5** - ładowanie i cache'owanie obrazów
- **VideoCache** - własna implementacja cache'owania wideo
- **android-gif-drawable 1.2.3** - obsługa GIF-ów
- **subsampling-scale-image-view 3.6.0** - zaawansowane wyświetlanie obrazów z zoomem

### Interfejs użytkownika:

- **Support Library 28.0.0** (AppCompat, CardView, RecyclerView, Design)
- **ButterKnife 8.4.0** - wiązanie widoków
- **MultiStateView 1.2.0** - zarządzanie stanami widoków
- **BottomSheet 2.3.1** - dolne panele
- **TextDrawable 1.1** - rysowanie tekstu jako drawable

### Baza danych:

- **SQLite** - własna implementacja bez ORM
- Singleton dla operacji bazodanowych

### Integracje:

- **Muzei API 2.0** - tapeta na żywo

## 5. Zasady kodowania dla tego repozytorium

### Podstawowe zasady:

1. **Zachowaj kod prosty** - unikaj niepotrzebnej złożoności
2. **Zachowaj kod minimalny** - usuwaj nieużywany kod
3. **Brak komentarzy w kodzie** - obecna konwencja projektu
4. **Unikaj overengineeringu** - rozwiązania powinny być adekwatne do problemu
5. **Preferuj małe, ukierunkowane zmiany** - łatwiejsze do przeglądu i testowania

### Zasady bezpieczeństwa zmian:

1. **Nie refaktoryzuj niepowiązanych plików** - zmiany powinny być skupione
2. **Zachowaj istniejące zachowanie** - o ile nie jest wyraźnie wymagana zmiana
3. **Testuj na urządzeniach z API 17+** - zgodność z minimalnym SDK
4. **Utrzymuj działające buildy** - każda zmiana powinna przechodzić kompilację

### Konwencje techniczne:

- **Java 1.7 compatibility** - nie używaj lambd ani stream API
- **Legacy Android plugin** - projekt używa starego stylu Gradle
- **Package naming** - zachowaj `com.puksh.pokenimgur` jako pakiet główny
- **App ID** - nie zmieniaj `com.puksh.poken.imgur`

## 6. Rozwiązywanie typowych problemów

### Błędy kompilacji:

- **JDK version mismatch**: Upewnij się, że `JAVA_HOME` wskazuje na JDK 8
- **Missing API keys**: Wymagane zmienne `API_CLIENT_ID` i `API_CLIENT_SECRET`
- **Gradle daemon issues**: Użyj flagi `--no-daemon` w problematycznych środowiskach

### Problemy z pamięcią podręczną:

- **Cache size**: 25MB dla obrazów
- **Video cache**: Własna implementacja `VideoCache`
- **Cache clearing**: Automatyczne czyszczenie co 3 dni

### Problemy z API:

- **OAuth token refresh**: Logika odświeżania tokenów w `OAuthInterceptor`
- **Rate limiting**: Obsługa limitów API Imgur
- **Network interceptors**: `TrafficStatsImageDownloader` dla statystyk sieciowych

## 7. Najważniejsze zmiany w forku (2026)

### Luty 2026 - główne ulepszenia:

1. **Usunięcie Fabric i Crashlytics** - deintegracja z usługami analytics
2. **Naprawy GIF-ów i wideo** - poprawa obsługi formatów multimedialnych
3. **Optymalizacje pamięci podręcznej** - zwiększenie do 25MB, lepsze prefetching
4. **Ulepszenia API** - poprawa strategii cache'owania, network interceptors
5. **Naprawy UI** - loading view dla fullscreen, poprawki thumbnaili
6. **Workflow CI/CD** - GitHub Actions dla automatycznych buildów APK

### Kluczowe funkcjonalności dodane:

- **Comments bez logowania** - możliwość przeglądania komentarzy bez konta
- **Media w komentarzach** - obrazy i wideo w komentarzach
- **Hardcoded topics** - tematy zakodowane na stałe z cache'owaniem
- **Improved pagination** - lepsze prefetching paginacji

## 8. Praktyczne wskazówki dla deweloperów

### Przy pracy z kodem:

- Używaj narzędzi do efektywnego wyszukiwania i zastępowania tekstu
- Sprawdzaj wpływ zmian na inne części systemu
- Testuj na rzeczywistych urządzeniach z różnymi wersjami Android

### Przy dodawaniu nowych funkcji:

- Zachowaj kompatybilność z API 17+
- Nie wprowadzaj nowych frameworków bez wyraźnej potrzeby
- Preferuj rozwiązania podobne do istniejących w kodzie

### Przy naprawianiu bugów:

- Reprodukuj problem przed wprowadzeniem zmian
- Sprawdź czy fix nie łamie istniejącej funkcjonalności
- Testuj edge cases związane z pamięcią i siecią

### Współpraca z istniejącym kodem:

- Szanuj istniejące wzorce architektoniczne
- Używaj podobnych nazewnictwa i struktury
- Zachowaj poziom abstrakcji zgodny z resztą projektu

---

**Ostatnia aktualizacja**: Marzec 2026  
**Stan projektu**: Aktywnie rozwijany  
**Kompatybilność**: Android 4.2+ (API 17+)  
**Język dokumentacji**: Polski (dla precyzji)
