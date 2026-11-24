package com.example.shizuku

import android.os.Binder
import android.os.IBinder
import android.os.IInterface
import android.os.Parcel
import android.os.RemoteException

/**
 * Pure Kotlin Binder IPC contract for ShizukuUserService.
 * Uses standard Android AIDL-compatible Binder wire format without requiring build-time `aidl` CLI.
 */
interface IShizukuInputService : IInterface {

    @Throws(RemoteException::class)
    fun injectTap(x: Float, y: Float, durationMs: Long): Boolean

    @Throws(RemoteException::class)
    fun isInputReady(): Boolean

    @Throws(RemoteException::class)
    fun destroy()

    abstract class Stub : Binder(), IShizukuInputService {

        init {
            attachInterface(this, DESCRIPTOR)
        }

        override fun asBinder(): IBinder = this

        @Throws(RemoteException::class)
        override fun onTransact(code: Int, data: Parcel, reply: Parcel?, flags: Int): Boolean {
            if (code in FIRST_CALL_TRANSACTION..LAST_CALL_TRANSACTION) {
                data.enforceInterface(DESCRIPTOR)
            }
            if (code == INTERFACE_TRANSACTION) {
                reply?.writeString(DESCRIPTOR)
                return true
            }
            return when (code) {
                TRANSACTION_injectTap -> {
                    val x = data.readFloat()
                    val y = data.readFloat()
                    val durationMs = data.readLong()
                    val result = injectTap(x, y, durationMs)
                    reply?.writeNoException()
                    reply?.writeInt(if (result) 1 else 0)
                    true
                }

                TRANSACTION_isInputReady -> {
                    val result = isInputReady()
                    reply?.writeNoException()
                    reply?.writeInt(if (result) 1 else 0)
                    true
                }

                TRANSACTION_destroy -> {
                    destroy()
                    reply?.writeNoException()
                    true
                }

                else -> super.onTransact(code, data, reply, flags)
            }
        }

        private class Proxy(private val remote: IBinder) : IShizukuInputService {
            override fun asBinder(): IBinder = remote

            override fun injectTap(x: Float, y: Float, durationMs: Long): Boolean {
                val data = Parcel.obtain()
                val reply = Parcel.obtain()
                return try {
                    data.writeInterfaceToken(DESCRIPTOR)
                    data.writeFloat(x)
                    data.writeFloat(y)
                    data.writeLong(durationMs)
                    remote.transact(TRANSACTION_injectTap, data, reply, 0)
                    reply.readException()
                    reply.readInt() != 0
                } finally {
                    reply.recycle()
                    data.recycle()
                }
            }

            override fun isInputReady(): Boolean {
                val data = Parcel.obtain()
                val reply = Parcel.obtain()
                return try {
                    data.writeInterfaceToken(DESCRIPTOR)
                    remote.transact(TRANSACTION_isInputReady, data, reply, 0)
                    reply.readException()
                    reply.readInt() != 0
                } finally {
                    reply.recycle()
                    data.recycle()
                }
            }

            override fun destroy() {
                val data = Parcel.obtain()
                val reply = Parcel.obtain()
                try {
                    data.writeInterfaceToken(DESCRIPTOR)
                    remote.transact(TRANSACTION_destroy, data, reply, 0)
                    reply.readException()
                } finally {
                    reply.recycle()
                    data.recycle()
                }
            }
        }

        companion object {
            const val DESCRIPTOR = "com.example.shizuku.IShizukuInputService"
            const val TRANSACTION_injectTap = FIRST_CALL_TRANSACTION + 0
            const val TRANSACTION_isInputReady = FIRST_CALL_TRANSACTION + 1
            const val TRANSACTION_destroy = FIRST_CALL_TRANSACTION + 2

            fun asInterface(obj: IBinder?): IShizukuInputService? {
                if (obj == null) return null
                val local = obj.queryLocalInterface(DESCRIPTOR)
                if (local is IShizukuInputService) {
                    return local
                }
                return Proxy(obj)
            }
        }
    }
}
