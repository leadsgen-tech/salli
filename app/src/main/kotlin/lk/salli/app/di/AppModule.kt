package lk.salli.app.di

import android.content.Context
import androidx.room.Room
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import lk.salli.app.BuildConfig
import lk.salli.data.ai.ModelManager
import lk.salli.data.ai.ModelProgressStore
import lk.salli.data.categorization.KeywordCategorizer
import lk.salli.data.categorization.TypeCategorizer
import lk.salli.data.db.SalliDatabase
import lk.salli.data.export.DataWiper
import lk.salli.data.export.TransactionExporter
import lk.salli.data.ingest.TransactionIngestor
import lk.salli.data.prefs.SalliPreferences
import lk.salli.data.seed.Seeder

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
    fun provideIngestor(
        db: SalliDatabase,
        categorizer: KeywordCategorizer,
        typeCategorizer: TypeCategorizer,
    ): TransactionIngestor = TransactionIngestor(
        db = db,
        categorizer = categorizer,
        typeCategorizer = typeCategorizer,
    )

    @Provides
    @Singleton
    fun provideExporter(
        db: SalliDatabase,
        @ApplicationContext context: Context,
    ): TransactionExporter = TransactionExporter(db = db, context = context)

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
    fun provideModelProgressStore(): ModelProgressStore = ModelProgressStore()

    @Provides
    @Singleton
    fun provideModelManager(
        @ApplicationContext context: Context,
        progressStore: ModelProgressStore,
    ): ModelManager = ModelManager(context, progressStore)
}
