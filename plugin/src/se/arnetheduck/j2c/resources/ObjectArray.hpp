#pragma once

#include <initializer_list>
#include <type_traits>

#include <java/lang/Object.hpp>
#include <java/lang/Cloneable.hpp>
#include <java/io/Serializable.hpp>

namespace java
{
	namespace lang
	{
		class ObjectArray;
	}
}

extern java::lang::Class *class_(const char16_t *c, int n);

class java::lang::ObjectArray
    : public virtual ::java::lang::Object
    , public virtual ::java::lang::Cloneable
    , public virtual ::java::io::Serializable
{
public:
    static ::java::lang::Class *class_() {
        static ::java::lang::Class* c = ::class_(u"java.lang.Object[]", 31);
        return c;
    }

    typedef ::java::lang::Object super;

    typedef Object *value_type;
    typedef value_type *pointer_type;
    typedef pointer_type iterator;
    typedef const value_type *const_pointer_type;
    typedef const_pointer_type const_iterator;

    typedef int size_type;

    ObjectArray() : length(0), p(nullptr) { }
    ObjectArray(int n) : length(n), p(n == 0 ? nullptr : new value_type[n])
    {
    	for(auto x = p; x != p + length; ++x) *x = value_type();
    }

    ObjectArray(const value_type *values, int n) : length(n), p(new value_type[n])
    {
    	auto x = p;
    	for(auto v = values; v != values + n; ++v) *x++ = *v;
    }

    template<typename T>
    ObjectArray(std::initializer_list<T> l) : length(l.size()), p(new value_type[l.size()])
    {
    	auto x = p;
    	for(auto v : l) *x++ = v;
    }

    ObjectArray(const ObjectArray &rhs) : ObjectArray(rhs.p, rhs.length)
    {
    }

    ObjectArray(ObjectArray &&rhs) : length(rhs.length), p(rhs.p)
    {
    	const_cast<pointer_type&>(rhs.p) = 0;
    }

    ObjectArray &operator=(const ObjectArray &rhs)
    {
        if(&rhs != this) {
            if(length != rhs.length) {
                delete p;
                const_cast<pointer_type&>(p) = 0;
                const_cast<size_type&>(length) = rhs.length;
                const_cast<pointer_type&>(p) = new value_type[length];
            }

            auto x = p;
            for(auto v = rhs.p; v != rhs.p + rhs.length; ++v) *x++ = *v;
        }

        return *this;
    }

    ObjectArray &operator=(ObjectArray &&rhs)
    {
        if(&rhs != this) {
            delete p;
            const_cast<size_type&>(length) = rhs.length;
            const_cast<pointer_type&>(p) = rhs.p;
            const_cast<pointer_type&>(rhs.p) = 0;
        }

        return *this;
    }

    virtual ~ObjectArray() { delete p; }

    ObjectArray* clone() override { return new ObjectArray(*this); }

    value_type operator[](size_type i) const { return get(i); }
    value_type get(size_type i) const { return p[i]; }

    template<typename T>
    T set(size_type i, T x) { set0(i, x); return x; }

    iterator        begin() { return p; }
    const_iterator  begin() const { return p; }
    const_iterator  cbegin() const { return begin(); }

    iterator        end() { return p + length; }
    const_iterator  end() const { return p + length; }
    const_iterator  cend() const { return end(); }

    const size_type length;
    const pointer_type p;

private:
    ::java::lang::Class *getClass0() override { return class_(); }

     virtual void set0(size_type i, Object *x) { p[i] = x; }
};

template<typename ArrayType>
ArrayType* __newMultiArray(int dim) {
	return new ArrayType(dim);
}

template<typename ArrayType, class... Dims>
ArrayType* __newMultiArray(int dim, Dims... dims) {
	auto ret = new ArrayType(dim);
	for (auto i = 0; i < dim; ++i) {
		ret->p[i] = __newMultiArray<typename std::remove_pointer<typename ArrayType::value_type>::type>(dims...);
	}
	return ret;
}
