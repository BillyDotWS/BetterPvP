package me.mykindos.betterpvp.core.framework.persistence;

import org.bukkit.configuration.serialization.ConfigurationSerializable;
import org.bukkit.persistence.PersistentDataAdapterContext;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.io.BukkitObjectInputStream;
import org.bukkit.util.io.BukkitObjectOutputStream;
import org.jetbrains.annotations.NotNull;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.reflect.Array;

/**
 * A {@link PersistentDataType} for {@link ConfigurationSerializable} arrays
 * @param <T> The type of the {@link ConfigurationSerializable}
 */
public class ConfigurationSerializableArrayDataType<T extends ConfigurationSerializable> implements PersistentDataType<byte[], T[]> {
    private final Class<T> type;
    private final Class<T[]> types;

    /**
     * Creates a new {@link ConfigurationSerializableArrayDataType} with the given type
     * @param types The type of the {@link ConfigurationSerializable}
     */
    @SuppressWarnings("unchecked")
    public ConfigurationSerializableArrayDataType(final Class<T[]> types) {
        this.type = (Class<T>) types.getComponentType();
        this.types = types;
    }

    @NotNull
    @Override
    public Class<byte[]> getPrimitiveType() {
        return byte[].class;
    }

    @NotNull
    @Override
    public Class<T[]> getComplexType() {
        return types;
    }

    @NotNull
    @Override
    public byte [] toPrimitive(@NotNull final T [] serializable, @NotNull final PersistentDataAdapterContext persistentDataAdapterContext) {
        try (final ByteArrayOutputStream outputStream = new ByteArrayOutputStream(); final BukkitObjectOutputStream bukkitObjectOutputStream = new BukkitObjectOutputStream(outputStream)) {
            bukkitObjectOutputStream.writeInt(serializable.length);
            for (final T t : serializable) {
                bukkitObjectOutputStream.writeObject(t);
            }
            return outputStream.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException(ConfigurationSerializableDataType.getExceptionMessage(type, ConfigurationSerializableDataType.SerializationType.SERIALIZATION), e);
        }
    }

    @NotNull
    @Override
    @SuppressWarnings("unchecked")
    public T [] fromPrimitive(@NotNull final byte [] bytes, @NotNull final PersistentDataAdapterContext persistentDataAdapterContext) {
        try (final ByteArrayInputStream inputStream = new ByteArrayInputStream(bytes); final BukkitObjectInputStream bukkitObjectInputStream = new BukkitObjectInputStream(inputStream)) {
            final T[] ts = (T[]) Array.newInstance(type, bukkitObjectInputStream.readInt());
            for (int i = 0; i < ts.length; i++) {
                ts[i] = (T) bukkitObjectInputStream.readObject();
            }
            return ts;
        } catch (final EOFException e) {
            return (T[]) Array.newInstance(getComplexType().getComponentType(), 0);
        } catch (final IOException e) {
            throw new UncheckedIOException(ConfigurationSerializableDataType.getExceptionMessage(type, ConfigurationSerializableDataType.SerializationType.DESERIALIZATION), e);
        } catch (final ClassNotFoundException e) {
            throw new RuntimeException(ConfigurationSerializableDataType.getExceptionMessage(type, ConfigurationSerializableDataType.SerializationType.DESERIALIZATION), e);
        }
    }
}