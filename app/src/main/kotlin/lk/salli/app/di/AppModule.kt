package lk.salli.app.di

import lk.salli.data.planning.PlanningService
import lk.salli.data.planning.RecurringService
import android.content.Context
import androidx.room.Room
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import lk.salli.app.BuildConfig
import lk.salli.data.categorization.KeywordCategorizer
import lk.salli.data.categorization.TypeCategorizer
import lk.salli.data.db.SalliDatabase
import lk.salli.data.export.DataWiper
import lk.salli.data.export.TransactionExporter
import lk.salli.data.export.ReviewExporter
import lk.salli.data.ingest.TransactionIngestor
import lk.salli.data.ingest.UtilityIngestor
import lk.salli.data.merchant.MerchantStatsService
import lk.salli.data.upcoming.UpcomingService
import lk.salli.data.backup.BackupManager
import lk.salli.data.prefs.SalliPreferences
import lk.salli.data.split.SplitService
import lk.salli.data.summary.SummaryService
import lk.salli.data.seed.Seeder
import lk.salli.data.widget.WidgetSummaryService

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): SalliDatabase =
        Room.databaseBuilder(context, SalliDatabase::class.java, SalliDatabase.NAME)
            .apply {
                // Release builds must never silently wipe user data on an unknown schema;
                // fail loud so we notice and ship a proper migration. Debug builds keep the
                // drop-on-mismatch behaviour so iteration stays fast.
                if (BuildConfig.DEBUG) fallbackToDestructiveMigration(dropAllTables = true)
            }
            .build()

    @Provides
    @Singleton
    fun provideSeeder(db: SalliDatabase): Seeder = Seeder(db)

    @Provides
    @Singleton
    fun provideCategorizer(db: SalliDatabase): KeywordCategorizer =
        KeywordCategorizer(db.keywords())

    @Provides
    @Singleton
    fun provideTypeCategorizer(db: SalliDatabase): TypeCategorizer =
        TypeCategorizer(db.categories())

    @Provides
    @Singleton
    fun provideUtilityIngestor(db: SalliDatabase): UtilityIngestor = UtilityIngestor(db)

    @Provides
    @Singleton
    fun provideIngestor(
        db: SalliDatabase,
        categorizer: KeywordCategorizer,
        typeCategorizer: TypeCategorizer,
        utilityIngestor: UtilityIngestor,
    ): TransactionIngestor = TransactionIngestor(
        db = db,
        categorizer = categorizer,
        typeCategorizer = typeCategorizer,
        utilityIngestor = utilityIngestor,
    )

    @Provides
    @Singleton
    fun provideExporter(
        db: SalliDatabase,
        @ApplicationContext context: Context,
    ): TransactionExporter = TransactionExporter(db = db, context = context)

    @Provides
    @Singleton
    fun provideReviewExporter(
        db: SalliDatabase,
        @ApplicationContext context: Context,
    ): ReviewExporter = ReviewExporter(db = db, context = context)

    @Provides
    @Singleton
    fun provideWiper(
        db: SalliDatabase,
        seeder: Seeder,
        prefs: SalliPreferences,
    ): DataWiper = DataWiper(db = db, seeder = seeder, prefs = prefs)

    @Provides
    @Singleton
    fun providePreferences(@ApplicationContext context: Context): SalliPreferences =
        SalliPreferences(context)

    @Provides
    @Singleton
    fun provideBackupManager(
        db: SalliDatabase,
        prefs: SalliPreferences,
        @ApplicationContext context: Context,
    ): BackupManager = BackupManager(db = db, prefs = prefs, context = context, appVersion = BuildConfig.VERSION_NAME)

    @Provides
    @Singleton
    fun provideSummaryService(db: SalliDatabase, prefs: SalliPreferences): SummaryService =
        SummaryService(db = db, prefs = prefs)

    @Provides
    @Singleton
    fun provideRecurringService(db: SalliDatabase): RecurringService = RecurringService(db)

    @Provides
    @Singleton
    fun providePlanningService(db: SalliDatabase, prefs: SalliPreferences): PlanningService =
        PlanningService(db = db, prefs = prefs)

    @Provides
    @Singleton
    fun provideSplitService(db: SalliDatabase): SplitService = SplitService(db)

    @Provides
    @Singleton
    fun provideWidgetSummaryService(
        db: SalliDatabase,
        prefs: SalliPreferences,
        planning: PlanningService,
    ): WidgetSummaryService = WidgetSummaryService(db = db, prefs = prefs, planning = planning)

    // The data module carries no Hilt, so its services are constructed here. Both of these also
    // take a clock / timezone that Hilt has no binding for.

    @Provides
    @Singleton
    fun provideUpcomingService(db: SalliDatabase): UpcomingService = UpcomingService(db)

    @Provides
    @Singleton
    fun provideMerchantStatsService(db: SalliDatabase): MerchantStatsService = MerchantStatsService(db)
}
