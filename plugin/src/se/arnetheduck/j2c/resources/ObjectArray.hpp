#pragma once

#include <algorithm>
#include <initializer_list>

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

class java::lang::ObjectArray
    : public virtual ::java::lang::Object
    , public virtual ::java::lang::Cloneable
    , public virtual ::java::io::Serializable
{
public:
    static ::java::lang::Class *class_();
    typedef ::java::lang::Object super;

    typedef Object *value_type;
    typedef value_type *pointer_type;
    typedef int size_type;

    ObjectArray() : length(0), p(nullptr) { }
    ObjectArray(int n) : length(n), p(new value_type[n]) { std::fill(p, p+length, nullptr); }

    ObjectArray(const value_type *p, int n) : length(n), p(new value_type[n])
    {
    	std::copy(p, p+n, this->p);
    }

    template<typename T>
    ObjectArray(std::initializer_list<T> l) : length(l.size()), p(new value_type[l.size()])
    {
    	std::copy(l.begin(), l.end(), p);
    }

    ObjectArray(const ObjectArray &rhs) : length(rhs.length), p(new value_type[rhs.length])
    {
    	std::copy(rhs.p, rhs.p + rhs.length, p);
    }

    ObjectArray(ObjectArray &&rhs) : length(rhs.length), p(rhs.p)
    {
    	const_cast<pointer_type&>(rhs.p) = 0;
    }

    ObjectArray &operator=(const ObjectArray &rhs)
    {
    	if(&rhs != this) {
			delete p;
			const_cast<pointer_type&>(p) = 0;
			const_cast<size_type&>(length) = rhs.length;
			const_cast<pointer_type&>(p) = new value_type[length];
			std::copy(rhs.p, rhs.p + length, p);
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

    const size_type length;
    const pointer_type p;

private:
     ::java::lang::Class *getClass0() override;

     virtual void set0(size_type i, Object *x) { p[i] = x; }
};
